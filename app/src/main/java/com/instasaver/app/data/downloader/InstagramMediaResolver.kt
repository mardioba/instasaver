package com.instasaver.app.data.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class InstagramMedia(
    /** Direct URL of the media file (mp4 for videos, jpg for photo posts). */
    val videoUrl: String,
    val thumbnailUrl: String?,
    val title: String?,
    /** False when the post is a photo (including photos with music). */
    val isVideo: Boolean = true
)

sealed class ResolveResult {
    data class Success(val media: InstagramMedia) : ResolveResult()
    object PrivateOrUnavailable : ResolveResult()
    object Unsupported : ResolveResult()
    data class NetworkError(val message: String) : ResolveResult()
    object ServerRefused : ResolveResult()
}

/**
 * Resolves public Instagram posts to their media URL.
 *
 * This implementation only works with publicly exposed metadata of a
 * public post (the Open Graph tags of the public page). It does not
 * bypass authentication, DRM or access controls. When Instagram does
 * not expose a public media URL, the content is reported as unavailable.
 */
class InstagramMediaResolver(
    private val client: OkHttpClient = defaultClient()
) {

    suspend fun resolve(
        postUrl: String,
        sessionId: String? = null,
        csrfToken: String? = null
    ): ResolveResult = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(postUrl)
                .header("User-Agent", UA)
                .header("Accept-Language", "en-US,en;q=0.9")
            if (!sessionId.isNullOrBlank()) {
                requestBuilder.header(
                    "Cookie",
                    "sessionid=$sessionId" + (if (csrfToken.isNullOrBlank()) "" else "; csrftoken=$csrfToken")
                )
            }
            val request = requestBuilder.get().build()
            client.newCall(request).execute().use { response ->
                android.util.Log.d("InstaSave", "HTTP ${response.code}, length=${response.body?.contentLength()}")
                val result = when (response.code) {
                    200 -> {
                        val html = response.body?.string() ?: ""
                        android.util.Log.d(
                            "InstaSave",
                            "finalUrl=${response.request.url}, hasOgVideo=${html.contains("og:video")}, hasVideoUrl=${html.contains("video_url")}, hasLogin=${html.contains("login/?next=")}, hasIsPrivateTrue=${html.contains("\"is_private\":true")}, hasOgTitle=${html.contains("og:title")}"
                        )
                        android.util.Log.d("InstaSave", "htmlPrefix=${html.take(400)}")
                        val titleM = Regex("<title>([^<]{0,120})</title>").find(html)
                        android.util.Log.d("InstaSave", "pageTitle=${titleM?.groupValues?.get(1)}")
                        parsePublicPage(html)
                    }
                    401, 403, 404 -> ResolveResult.PrivateOrUnavailable
                    429 -> ResolveResult.ServerRefused
                    else -> ResolveResult.Unsupported
                }
                android.util.Log.d("InstaSave", "resolve=$result")
                result
            }
        } catch (e: java.io.IOException) {
            ResolveResult.NetworkError(e.message ?: "network error")
        } catch (e: Exception) {
            ResolveResult.Unsupported
        }
    }

    internal fun parsePublicPage(html: String): ResolveResult {
        val videoUrl = extractMeta(html, "og:video")
            ?: extractMeta(html, "og:video:secure_url")
            ?: extractJsonString(html, "video_url")
            ?: extractJsonString(html, "video_url_hq")
            ?: extractMp4Direct(html)
        if (videoUrl != null) {
            return ResolveResult.Success(
                InstagramMedia(videoUrl, extractMeta(html, "og:image"), extractMeta(html, "og:title"))
            )
        }

        // Photo posts (including photos with music) expose only an image URL,
        // never a video. Prefer the high-resolution display_url from the page
        // data, falling back to the og:image tag. Only accept URLs from the
        // media CDN (scontent) so login/interstitial pages (which point to
        // static.cdninstagram.com assets) are not mistaken for posts.
        val imageUrl = (extractJsonString(html, "display_url") ?: extractMeta(html, "og:image"))
            ?.takeIf { it.contains("scontent") }
        if (imageUrl != null) {
            return ResolveResult.Success(
                InstagramMedia(imageUrl, imageUrl, extractMeta(html, "og:title"), isVideo = false)
            )
        }

        return when {
            html.contains("\"is_private\":true") -> ResolveResult.PrivateOrUnavailable
            html.contains("login/?next=") || html.contains("/accounts/login/") ->
                ResolveResult.PrivateOrUnavailable
            else -> ResolveResult.Unsupported
        }
    }

    private fun extractMeta(html: String, property: String): String? {
        val escaped = Regex.escape(property)
        val m = Regex("<meta[^>]+property=\"$escaped\"[^>]+content=\"([^\"]+)\"").find(html)
            ?: Regex("<meta[^>]+content=\"([^\"]+)\"[^>]+property=\"$escaped\"").find(html)
        return m?.groupValues?.get(1)?.replace("&amp;", "&")
    }

    private fun extractJsonString(html: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(html) ?: return null
        return m.groupValues[1]
            .replace("\\/", "/")
            .replace("\\u0025", "%")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
    }

    private fun extractMp4Direct(html: String): String? {
        val m = Regex("(https?:[^\"']*?\\.mp4[^\"']*)").find(html) ?: return null
        return m.groupValues[1]
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .takeIf { it.contains("scontent") || it.contains("instagram") }
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
