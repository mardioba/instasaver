package com.instasaver.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.instasaver.app.ui.download.DownloadScreen
import com.instasaver.app.ui.history.HistoryScreen
import com.instasaver.app.ui.home.HomeScreen
import com.instasaver.app.ui.player.PlayerScreen
import com.instasaver.app.ui.settings.SettingsRepository
import com.instasaver.app.ui.settings.SettingsScreen
import com.instasaver.app.ui.theme.InstaSaveTheme
import androidx.compose.foundation.layout.fillMaxWidth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        val settingsRepo = SettingsRepository(applicationContext)

        setContent {
            val settings by settingsRepo.state.collectAsState(initial = com.instasaver.app.ui.settings.SettingsState())

            val navController = rememberNavController()
            InstaSaveTheme(mode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                Triple("home", Icons.Filled.Home, "Início"),
                                Triple("history", Icons.Filled.Folder, "Downloads"),
                                Triple("settings", Icons.Filled.Settings, "Ajustes")
                            ).forEach { (route, icon, label) ->
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { navController.navigate(route) { popUpTo("home") { inclusive = route == "home" } } },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val sharedUrl = parseShareIntent(intent)
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (sharedUrl != null) navController.navigate("download/${Uri.encode(sharedUrl)}")
                    }
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = androidx.compose.ui.Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(onOpenLink = { url ->
                                navController.navigate("download/${Uri.encode(url)}")
                            })
                        }
                        composable("history") {
                            HistoryScreen(onOpenPlayer = { uri ->
                                navController.navigate("player/${Uri.encode(uri.toString())}")
                            })
                        }
                        composable("settings") {
                            SettingsScreen(onNavigateToLogin = {
                                val ctx = navController.context
                                ctx.startActivity(
                                    android.content.Intent(ctx, com.instasaver.app.ui.settings.LoginActivity::class.java)
                                )
                            })
                        }
                        composable("download/{url}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("url").orEmpty()
                            DownloadScreen(
                                url = Uri.decode(encoded),
                                onOpenPlayer = { uri ->
                                    navController.navigate("player/${Uri.encode(uri.toString())}")
                                }
                            )
                        }
                        composable("player/{uri}") { backStackEntry ->
                            val encoded = backStackEntry.arguments?.getString("uri").orEmpty()
                            PlayerScreen(uri = Uri.parse(Uri.decode(encoded)))
                        }
                    }
                }
            }
        }
    }

    private fun parseShareIntent(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                if (com.instasaver.app.data.downloader.InstagramUrlParser.isInstagramUrl(text)) {
                    return text
                }
                Toast.makeText(this, "Link compartilhado não é do Instagram.", Toast.LENGTH_LONG).show()
            }
        }
        return null
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
