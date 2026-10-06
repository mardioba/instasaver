package com.instasaver.app.ui.download

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.instasaver.app.R
import java.util.Locale

@Composable
fun DownloadScreen(
    url: String,
    onOpenPlayer: (Uri) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: DownloadViewModel = viewModel(
        key = "download-$url",
        factory = DownloadViewModel.Factory(app, url)
    )
    val state by viewModel.state.collectAsState()

    val workInfoLive = viewModel.workInfoLiveData
    val workInfoState = if (workInfoLive != null) workInfoLive.observeAsState() else null
    LaunchedEffect(workInfoState?.value) {
        viewModel.onWorkInfo(workInfoState?.value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val s = state) {
            is DownloadUiState.Resolving -> {
                Text("Obtendo informações do vídeo...", style = MaterialTheme.typography.titleMedium)
                CircularProgressIndicator()
            }

            is DownloadUiState.Ready -> {
                val media = s.media
                media.thumbnailUrl?.let { thumb ->
                    AsyncImage(
                        model = thumb,
                        contentDescription = "miniatura",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                media.title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(viewModel.sourceUrl, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::startDownload, modifier = Modifier.fillMaxWidth()) {
                    Text("Baixar")
                }
            }

            is DownloadUiState.Downloading -> {
                Text("Baixando vídeo...", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { if (s.total > 0) s.progress / 100f else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                val pct = if (s.total > 0) " ${s.progress}%" else ""
                Text("${formatSize(s.downloaded)}${if (s.total > 0) " / ${formatSize(s.total)}" else ""}${pct}")
                Text("Velocidade: ${formatSpeed(s.speed)}")
                OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar")
                }
            }

            is DownloadUiState.Complete -> {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Text("Download concluído", style = MaterialTheme.typography.titleLarge)
                Text(s.fileName)
                Button(onClick = { onOpenPlayer(s.uri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir vídeo")
                }
                OutlinedButton(
                    onClick = { shareVideo(context, s.uri) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Compartilhar") }
                OutlinedButton(
                    onClick = { openDownloadsFolder(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir pasta") }
            }

            is DownloadUiState.Failed -> {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )
                Text(s.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

fun formatSpeed(bytesPerSec: Long): String {
    val mb = bytesPerSec / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB/s", mb)
}

fun shareVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "video", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar vídeo"))
}

fun openDownloadsFolder(context: Context) {
    return try {
        val path = Uri.parse("content://com.android.externalstorage.documents/document/primary:Movies%2FInstaSave%20BR")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(path, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir a pasta.", Toast.LENGTH_SHORT).show()
    }
}
