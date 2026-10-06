package com.instasaver.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.instasaver.app.data.downloader.InstagramUrlParser

@Composable
fun HomeScreen(
    onOpenLink: (String) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("InstaSave BR", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Cole um link do Instagram", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://www.instagram.com/reel/...") },
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val parsed = InstagramUrlParser.parse(text.text)
                when (parsed) {
                    is InstagramUrlParser.Result.Valid -> onOpenLink(parsed.normalizedUrl)
                    is InstagramUrlParser.Result.NotInstagram ->
                        Toast.makeText(context, "URL que não é do Instagram.", Toast.LENGTH_SHORT).show()
                    is InstagramUrlParser.Result.Invalid ->
                        Toast.makeText(context, "URL inválida.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Baixar")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Ou compartilhe diretamente do Instagram para o app.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
