package com.instasaver.app.ui.history

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.instasaver.app.data.model.DownloadItem
import com.instasaver.app.ui.download.formatSize
import com.instasaver.app.ui.download.shareVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onOpenPlayer: (Uri) -> Unit
) {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(context.applicationContext as Application)
    )
    val items by viewModel.history.collectAsState()

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Nenhum download ainda.")
        }
        return
    }

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            HistoryRow(item, onOpenPlayer, viewModel)
        }
    }
}

@Composable
private fun HistoryRow(item: DownloadItem, onOpenPlayer: (Uri) -> Unit, viewModel: HistoryViewModel) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = Uri.parse(item.mediaStoreUri),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .height(64.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)) {
                Text(item.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                Text("$date • ${formatSize(item.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onOpenPlayer(Uri.parse(item.mediaStoreUri)) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "reproduzir")
            }
            IconButton(onClick = { shareVideo(context, Uri.parse(item.mediaStoreUri)) }) {
                Icon(Icons.Filled.Share, contentDescription = "compartilhar")
            }
            IconButton(onClick = { viewModel.delete(item) }) {
                Icon(Icons.Filled.Delete, contentDescription = "excluir")
            }
        }
    }
}
