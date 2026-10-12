package com.instasaver.app.data.downloader

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Renders the public (logged-in) Instagram post page in a headless WebView,
 * where Instagram decides the URL of the media, then pulls the resolved media
 * URL out of the page metadata. Cookies from the WebView CookieManager
 * (stored by the optional LoginActivity) are used automatically.
 */
class WebViewResolver(private val context: Context) {

    suspend fun resolve(postUrl: String, timeoutMillis: Long = 20000): ResolveResult =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            main.post {
                var done = false
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                        ) {
                            android.util.Log.e("InstaSave", "webviewresolver error: $errorCode $description $failingUrl")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d("InstaSave", "webviewresolver page finished: $url")
                            if (done) return
                            view?.evaluateJavascript("document.title") { titleJson ->
                                val postTitle = titleJson.trim('"').replace("\\u0026", "&")
                                android.util.Log.d("InstaSave", "webviewresolver title json=$postTitle")
                            }
                            view?.evaluateJavascript("document.documentElement.outerHTML") { htmlJson ->
                                if (done) return@evaluateJavascript
                                done = true
                                val cleaned = htmlJson.trim('"')
                                    .replace("\\\\/", "/")
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                android.util.Log.d("InstaSave", "webviewresolver cleaned hasVideoVersions=${cleaned.contains("video_versions")}, hasMp4=${cleaned.contains(".mp4")}, len=${cleaned.length}")
                                val list = Regex("https?://[^\"'\\s<>]+?\\.mp4[^\"'\\s<>]*").findAll(cleaned).map { it.value }.toList()
                                android.util.Log.d("InstaSave", "webviewresolver mp4 candidates: ${list.take(3)}")
                                val videoFound = list.firstOrNull()
                                if (!videoFound.isNullOrBlank() && cont.isActive) {
                                    cont.resume(ResolveResult.Success(InstagramMedia(videoFound, null, null)))
                                } else {
                                    // Photo posts (including photos with music) expose only an
                                    // image; grab the highest-quality media-CDN image we can
                                    // find, preferring post images over avatars.
                                    val candidates = Regex("https?://[^\"'\\s<>]+?\\.(?:jpg|jpeg|webp)[^\"'\\s<>]*")
                                        .findAll(cleaned)
                                        .map { it.value }
                                        .filter { it.contains("scontent") }
                                        .toList()
                                    val imageFound = candidates.firstOrNull { it.contains("/v/t51.2885") }
                                        ?: candidates.firstOrNull()
                                    android.util.Log.d("InstaSave", "webviewresolver image candidates: $imageFound")
                                    if (imageFound != null && cont.isActive) {
                                        cont.resume(ResolveResult.Success(InstagramMedia(imageFound, null, null, isVideo = false)))
                                    } else if (cont.isActive) {
                                        cont.resume(ResolveResult.Unsupported)
                                    }
                                }
                                destroy()
                            }
                        }
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(postUrl)
                }
                cont.invokeOnCancellation {
                    main.post { if (!done) { done = true; webView.destroy() } }
                }
                main.postDelayed({
                    if (cont.isActive && !done) {
                        done = true
                        webView.destroy()
                        cont.resume(ResolveResult.Unsupported)
                    }
                }, timeoutMillis)
            }
        }
}
