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

    private val resolver = InstagramMediaResolver()

    suspend fun resolve(postUrl: String, timeoutMillis: Long = 30000): ResolveResult =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            val startMs = System.currentTimeMillis()
            main.post {
                var done = false
                var attempt = 0
                var webView: WebView? = null

                fun finish(result: ResolveResult) {
                    if (done) return
                    done = true
                    webView?.destroy()
                    webView = null
                    if (cont.isActive) cont.resume(result)
                }

                fun accept(raw: String, attemptNumber: Int) {
                    val kind = raw.substringBefore('|', "")
                    val url = raw.substringAfter('|', "")
                    val media = if (url.startsWith("http")) {
                        resolver.mediaFromJsUrl(url, isVideo = kind == "V")
                    } else {
                        null
                    }
                    if (media != null) {
                        DiagLog.log(context, "webviewresolver achou mídia (#$attemptNumber): ${url.take(130)}")
                        finish(ResolveResult.Success(media))
                    } else {
                        DiagLog.log(context, "webviewresolver mídia rejeitada (#$attemptNumber): ${raw.take(140)}")
                        finish(ResolveResult.Unsupported)
                    }
                }

                fun probe() {
                    val view = webView
                    if (done || view == null) return
                    attempt++
                    // O Instagram monta o post depois do load (React). Por isso
                    // sondamos repetidamente até a mídia aparecer no DOM.
                    if (attempt == 1 || attempt % 10 == 0) {
                        view.evaluateJavascript(DIAG_JS) { diag ->
                            DiagLog.log(context, "webviewresolver diag(#$attempt): $diag")
                        }
                    }
                    view.evaluateJavascript(PROBE_JS) { json ->
                        if (done) return@evaluateJavascript
                        val raw = unescape(json)
                        if (raw.startsWith("V|") || raw.startsWith("I|")) {
                            accept(raw, attempt)
                        } else if (System.currentTimeMillis() - startMs >= timeoutMillis) {
                            DiagLog.log(context, "webviewresolver sem mídia após $attempt tentativas")
                            finish(ResolveResult.Unsupported)
                        } else {
                            main.postDelayed({ probe() }, PROBE_INTERVAL_MS)
                        }
                    }
                }

                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                        ) {
                            DiagLog.log(context, "webviewresolver error: $errorCode $description $failingUrl")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            DiagLog.log(context, "webviewresolver page finished: $url")
                            if (done) return
                            // O Instagram é um app React: entrega a casca do HTML e monta o
                            // post DEPOIS, de forma assíncrona. Rodar o JS uma única vez no
                            // onPageFinished enxerga a página vazia (confirmado em log:
                            // imgs=[], resources=[] 15ms após o load). Por isso fazemos
                            // polling até a mídia aparecer no DOM.
                            main.postDelayed({ probe() }, FIRST_PROBE_DELAY_MS)
                        }
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(postUrl)
                }

                cont.invokeOnCancellation { main.post { finish(ResolveResult.Unsupported) } }
                main.postDelayed(
                    {
                        if (cont.isActive && !done) {
                            DiagLog.log(context, "webviewresolver timeout geral após $attempt tentativas")
                            finish(ResolveResult.Unsupported)
                        }
                    },
                    timeoutMillis + 5000
                )
            }
        }

    /**
     * `evaluateJavascript` devolve o valor JSON-encoded (uma string entre aspas,
     * com `\/`, `\"` e `\\` escapados). Precisamos do texto cru para manipular
     * a URL.
     */
    private fun unescape(json: String): String = json.trim('"')
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    companion object {
        private const val FIRST_PROBE_DELAY_MS = 1500L
        private const val PROBE_INTERVAL_MS = 1200L
    }
}
