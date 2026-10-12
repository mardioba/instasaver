package com.instasaver.app.ui.download

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.instasaver.app.data.downloader.InstagramMedia
import com.instasaver.app.data.downloader.InstagramMediaResolver
import com.instasaver.app.data.downloader.InstagramUrlParser
import com.instasaver.app.data.downloader.InstagramOEmbedResolver
import com.instasaver.app.data.downloader.ResolveResult
import com.instasaver.app.data.downloader.WebViewResolver
import com.instasaver.app.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

sealed class DownloadUiState {
    object Resolving : DownloadUiState()
    data class Ready(val media: InstagramMedia) : DownloadUiState()
    data class Downloading(
        val progress: Int,
        val downloaded: Long,
        val total: Long,
        val speed: Long
    ) : DownloadUiState()
    data class Complete(val uri: Uri, val fileName: String, val isVideo: Boolean = true) : DownloadUiState()
    data class Failed(val message: String) : DownloadUiState()
}

class DownloadViewModel(
    app: Application,
    private val urlArg: String
) : AndroidViewModel(app) {

    private val repo = DownloadRepository(app)
    private val resolver = InstagramMediaResolver()
    private val oEmbedResolver = InstagramOEmbedResolver()
    private val webViewResolver = WebViewResolver(app)
    private val settingsRepo = com.instasaver.app.ui.settings.SettingsRepository(app)

    private val rawUrl: String = urlArg

    private val _state = MutableStateFlow<DownloadUiState>(DownloadUiState.Resolving)
    val state: StateFlow<DownloadUiState> = _state

    var sourceUrl: String = rawUrl
        private set
    private var preparedMedia: InstagramMedia? = null
    private var workId: UUID? = null
    private var _workInfoLiveData: LiveData<WorkInfo?>? = null
    val workInfoLiveData: LiveData<WorkInfo?>? get() = _workInfoLiveData

    init {
        viewModelScope.launch { resolve() }
    }

    private suspend fun resolve() {
        _state.value = DownloadUiState.Resolving
        when (val parsed = InstagramUrlParser.parse(rawUrl)) {
            is InstagramUrlParser.Result.NotInstagram -> {
                _state.value = DownloadUiState.Failed("Esta URL não é do Instagram.")
                return
            }
            is InstagramUrlParser.Result.Invalid -> {
                _state.value = DownloadUiState.Failed("Link inválido. Verifique e tente novamente.")
                return
            }
            is InstagramUrlParser.Result.Valid -> sourceUrl = parsed.normalizedUrl
        }
        val st = try { settingsRepo.state.first() } catch (_: Exception) { null }
        if (st != null && st.accessToken.isNotBlank()) {
            when (val r2 = oEmbedResolver.resolve(sourceUrl, st.accessToken)) {
                is ResolveResult.Success -> {
                    preparedMedia = r2.media
                    _state.value = DownloadUiState.Ready(r2.media)
                    return
                }
                else -> {
                    _state.value = DownloadUiState.Resolving
                }
            }
        }
        android.util.Log.d("InstaSave", "sourceUrl=$sourceUrl, loggedIn=${st?.loggedIn == true}")
        when (val result = resolver.resolve(sourceUrl, st?.sessionId, st?.csrfToken)) {
            is ResolveResult.Success -> {
                preparedMedia = result.media
                _state.value = DownloadUiState.Ready(result.media)
            }
            ResolveResult.PrivateOrUnavailable -> {
                android.util.Log.d("InstaSave", "trying WebView fallback")
                val wv = webViewResolver.resolve(sourceUrl)
                android.util.Log.d("InstaSave", "webView fallback result=$wv")
                if (wv is ResolveResult.Success) {
                    preparedMedia = wv.media
                    _state.value = DownloadUiState.Ready(wv.media)
                } else {
                    _state.value = DownloadUiState.Failed(
                        "Não foi possível obter esta mídia.\nVerifique se a publicação é pública e tente novamente."
                    )
                }
            }
            ResolveResult.Unsupported -> {
                android.util.Log.d("InstaSave", "trying WebView fallback")
                when (val wv = webViewResolver.resolve(sourceUrl)) {
                    is ResolveResult.Success -> {
                        android.util.Log.d("InstaSave", "webView fallback result=$wv")
                        preparedMedia = wv.media
                        _state.value = DownloadUiState.Ready(wv.media)
                    }
                    else -> {
                        android.util.Log.d("InstaSave", "webView fallback result=$wv")
                        _state.value = DownloadUiState.Failed(
                            "Este conteúdo não pode ser baixado pelo aplicativo (sem URL pública de mídia)."
                        )
                    }
                }
            }
            is ResolveResult.NetworkError ->
                _state.value = DownloadUiState.Failed("Sem conexão com a internet.")
            ResolveResult.ServerRefused ->
                _state.value = DownloadUiState.Failed(
                    "O servidor recusou a solicitação. Tente novamente em alguns minutos."
                )
        }
    }

    fun startDownload() {
        val media = preparedMedia ?: return
        workId = repo.startDownload(sourceUrl, media.videoUrl, media.title, media.isVideo)
        _workInfoLiveData = repo.workInfo(workId!!)
        _state.value = DownloadUiState.Downloading(0, 0, -1, 0)
    }

    fun cancel() {
        workId?.let { repo.cancelDownload(it) }
        _state.value = DownloadUiState.Failed("Download cancelado.")
    }

    fun onWorkInfo(info: WorkInfo?) {
        if (info == null) return
        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                _state.value = DownloadUiState.Downloading(0, 0, -1, 0)
            }
            WorkInfo.State.RUNNING -> {
                val p = info.progress
                _state.value = DownloadUiState.Downloading(
                    progress = p.getInt(com.instasaver.app.data.downloader.DownloadWorker.KEY_PROGRESS, 0),
                    downloaded = p.getLong(com.instasaver.app.data.downloader.DownloadWorker.KEY_DOWNLOADED, 0),
                    total = p.getLong(com.instasaver.app.data.downloader.DownloadWorker.KEY_TOTAL, -1),
                    speed = p.getLong(com.instasaver.app.data.downloader.DownloadWorker.KEY_SPEED, 0)
                )
            }
            WorkInfo.State.SUCCEEDED -> {
                val uri = info.outputData.getString(com.instasaver.app.data.downloader.DownloadWorker.KEY_FILE_URI)
                val name = info.outputData.getString(com.instasaver.app.data.downloader.DownloadWorker.KEY_FILE_NAME) ?: "video.mp4"
                if (uri != null) {
                    _state.value = DownloadUiState.Complete(Uri.parse(uri), name, preparedMedia?.isVideo ?: true)
                } else {
                    _state.value = DownloadUiState.Failed("Formato não suportado ou arquivo salvo sem URI.")
                }
            }
            WorkInfo.State.FAILED -> {
                val err = info.outputData.getString(com.instasaver.app.data.downloader.DownloadWorker.KEY_ERROR)
                _state.value = DownloadUiState.Failed(
                    when (err) {
                        "network" -> "Conexão perdida. Verifique sua internet."
                        "storage" -> "Falta de espaço no armazenamento?"
                        "server_refused" -> "Servidor recusando a solicitação."
                        "cancelled" -> "Download interrompido."
                        else -> "Não foi possível baixar este vídeo."
                    }
                )
            }
            WorkInfo.State.CANCELLED -> {
                _state.value = DownloadUiState.Failed("Download cancelado.")
            }
        }
    }

    class Factory(
        private val app: Application,
        private val url: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DownloadViewModel(app, url) as T
    }
}
