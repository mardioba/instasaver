package com.instasaver.app.data.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Resolve using Meta's official Instagram oEmbed endpoint.
 * Requires an access token granted by a Meta for Developers app.
 * https://developers.facebook.com/docs/instagram/sharing/oembed
 */
class InstagramOEmbedResolver(
    private val client: OkHttpClient = InstagramMediaResolver.defaultClient()
) {

    suspend fun resolve(postUrl: String, accessToken: String): ResolveResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://graph.facebook.com/v19.0/instagram_oembed?url=" +
                URLEncoder.encode(postUrl, "UTF-8") +
                "&access_token=" + accessToken
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ResolveResult.ServerRefused
                } else {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    extractFromEmbed(json)
                }
            }
        } catch (e: IOException) {
            ResolveResult.NetworkError(e.message ?: "network error")
        } catch (e: Exception) {
            ResolveResult.Unsupported
        }
    }

    private fun extractFromEmbed(json: JSONObject): ResolveResult {
        val html = json.optString("html", "")
        val title = json.optString("title", "").ifBlank { null }
        val thumb = json.optString("thumbnail_url", "").ifBlank { null }
        val maybe = Regex("(https?:[^\"']*?\\.mp4[^\"']*)").find(html)?.groupValues?.get(1)
            ?.replace("\\/", "/")?.replace("&amp;", "&")
        return if (maybe != null) {
            ResolveResult.Success(InstagramMedia(maybe, thumb, title))
        } else {
            ResolveResult.Unsupported
        }
    }
}
