package com.instasaver.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.instasaver.app.data.downloader.DownloadWorker
import com.instasaver.app.data.local.AppDatabase
import com.instasaver.app.data.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class DownloadRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).downloadDao()
    private val workManager = WorkManager.getInstance(context)

    val history: Flow<List<DownloadItem>> = dao.observeAll()

    fun startDownload(sourceUrl: String, videoUrl: String, title: String?): UUID {
        val data = Data.Builder()
            .putString(DownloadWorker.KEY_VIDEO_URL, videoUrl)
            .putString(DownloadWorker.KEY_SOURCE_URL, sourceUrl)
            .putString(DownloadWorker.KEY_TITLE, title)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(data)
            .build()
        workManager.enqueue(request)
        return request.id
    }

    fun cancelDownload(id: UUID) {
        workManager.cancelWorkById(id)
    }

    fun workInfo(id: UUID): LiveData<WorkInfo?> =
        workManager.getWorkInfoByIdLiveData(id)

    suspend fun delete(item: DownloadItem) {
        try {
            context.contentResolver.delete(Uri.parse(item.mediaStoreUri), null, null)
        } catch (_: Exception) {
        }
        dao.delete(item)
    }
}
