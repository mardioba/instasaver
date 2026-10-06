package com.instasaver.app.ui.settings

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/**
 * Plain (non-Compose) Activity hosting a dedicated WebView so the black-screen
 * issue seen inside Compose AndroidView is avoided.
 */
class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            setBackgroundColor(android.graphics.Color.WHITE)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    android.util.Log.d("InstaSave", "login page loaded: $url")
                    val cookieStr = CookieManager.getInstance().getCookie("https://www.instagram.com")
                    if (cookieStr != null && cookieStr.contains("sessionid=")) {
                        val sessionId = cookieStr.split(';')
                            .firstOrNull { it.trim().startsWith("sessionid=") }
                            ?.substringAfter("sessionid=")?.trim()
                        val csrf = cookieStr.split(';')
                            .firstOrNull { it.trim().startsWith("csrftoken=") }
                            ?.substringAfter("csrftoken=")?.trim()
                        if (sessionId != null) {
                            kotlinx.coroutines.runBlocking {
                                SettingsRepository(this@LoginActivity).setSession(sessionId, csrf ?: "")
                            }
                            finish()
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?, errorCode: Int, description: String?, failingUrl: String?
                ) {
                    android.util.Log.e("InstaSave", "login webview error: $errorCode $description $failingUrl")
                }
            }
            loadUrl("https://www.instagram.com/accounts/login/")
        }
        setContentView(webView)
    }
}
