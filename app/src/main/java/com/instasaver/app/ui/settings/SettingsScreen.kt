package com.instasaver.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.instasaver.app.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    onNavigateToLogin: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Aparência", style = MaterialTheme.typography.titleMedium)
        if (state.loggedIn) {
            Text("Conta Instagram conectada.", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = viewModel::clearSession) { Text("Sair da conta") }
        } else {
            androidx.compose.material3.TextButton(onClick = onNavigateToLogin) { Text("Entrar no Instagram (opcional)") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            ThemeMode.entries.forEach { mode ->
                TextButton(
                    onClick = { viewModel.setThemeMode(mode) },
                    enabled = mode != state.themeMode
                ) {
                    Text(mode.name)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Cor dinâmica", modifier = Modifier.weight(1f))
            Switch(checked = state.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
        }
        Text(
            "Use o botão Compartilhar do Instagram para enviar links para o app.",
            style = MaterialTheme.typography.bodyMedium
        )
        TokenField(state.accessToken, viewModel::setAccessToken)
    }
}

@Composable
private fun TokenField(token: String, onSave: (String) -> Unit) {
    var value by androidx.compose.runtime.remember(token) { androidx.compose.runtime.mutableStateOf(token) }
        androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text("Token de acesso oficial (oEmbed)") },
        modifier = Modifier.fillMaxWidth()
    )
    androidx.compose.material3.TextButton(onClick = { onSave(value) }) {
        Text("Salvar token")
    }
}
