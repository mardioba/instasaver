package com.instasaver.app.ui.settings

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Opens the Instagram login page in a WebView and stores the web session
 * cookies so public post pages can be fetched with the user's session.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
    ) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                setBackgroundColor(android.graphics.Color.WHITE)
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
                                    SettingsRepository(context).setSession(sessionId, csrf ?: "")
                                }
                                onLoggedIn()
                            }
                        }
                    }
                }
                loadUrl("https://www.instagram.com/accounts/login/")
            }
        }
    )
    }
}
