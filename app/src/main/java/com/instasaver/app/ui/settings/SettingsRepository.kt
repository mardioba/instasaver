package com.instasaver.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.instasaver.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accessToken: String = "",
    val sessionId: String = "",
    val csrfToken: String = ""
) {
    val loggedIn: Boolean get() = sessionId.isNotBlank()
}

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = booleanPreferencesKey("dynamic_color")
    private val tokenKey = stringPreferencesKey("access_token")
    private val sessionKey = stringPreferencesKey("sessionid")
    private val csrfKey = stringPreferencesKey("csrftoken")

    val state: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            themeMode = ThemeMode.entries.find { it.name == prefs[themeKey] } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[dynamicKey] ?: true,
            accessToken = prefs[tokenKey] ?: "",
            sessionId = prefs[sessionKey] ?: "",
            csrfToken = prefs[csrfKey] ?: ""
        )
    }

    suspend fun setSession(sessionId: String, csrfToken: String) {
        context.dataStore.edit {
            it[sessionKey] = sessionId
            it[csrfKey] = csrfToken
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(sessionKey)
            it.remove(csrfKey)
        }
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        } catch (_: Exception) { }
    }

    suspend fun setAccessToken(token: String) {
        context.dataStore.edit { it[tokenKey] = token }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = enabled }
    }
}
