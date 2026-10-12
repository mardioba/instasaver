package com.instasaver.app.data.downloader

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.instasaver.app.InstaSaveApp
import com.instasaver.app.MainActivity
import com.instasaver.app.R
import com.instasaver.app.data.local.AppDatabase
import com.instasaver.app.data.model.DownloadItem
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {

    @Throws(IOException::class)
    override suspend fun doWork(): Result = coroutineScope {
        val videoUrl = inputData.getString(KEY_VIDEO_URL)
            ?: return@coroutineScope Result.failure(workDataOf(KEY_ERROR to "missing_url"))
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: ""
        val friendlyTitle = inputData.getString(KEY_TITLE)
        val isVideo = inputData.getBoolean(KEY_IS_VIDEO, true)

        setForeground(createForegroundInfo(0, 0, 0, isVideo))

        val fileName = buildFileName(friendlyTitle, isVideo)
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val subDir = if (isVideo) {
            Environment.DIRECTORY_MOVIES + "/InstaSave BR"
        } else {
            Environment.DIRECTORY_PICTURES + "/InstaSave BR"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = applicationContext.contentResolver
        val uri: Uri? = resolver.insert(collection, values)

        if (uri == null) {
            return@coroutineScope Result.failure(workDataOf(KEY_ERROR to "storage"))
        }

        var downloaded = 0L
        var total = -1L
        var lastUpdate = 0L
        var lastBytes = 0L
        var speed = 0.0

        try {
            val request = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; InstaSaveBR)")
                .get()
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    resolver.delete(uri, null, null)
                    val err = if (response.code == 403 || response.code == 401) "server_refused" else "unsupported"
                    return@use Result.failure(workDataOf(KEY_ERROR to err))
                }
                total = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val body = response.body ?: return@use Result.failure(workDataOf(KEY_ERROR to "unsupported"))

                resolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (isStopped) {
                                resolver.delete(uri, null, null)
                                return@coroutineScope Result.failure(workDataOf(KEY_ERROR to "cancelled"))
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 500) {
                                val dt = (now - lastUpdate) / 1000.0
                                if (dt > 0) speed = (downloaded - lastBytes) / dt
                                lastUpdate = now
                                lastBytes = downloaded
                                val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
                                setProgress(
                                    workDataOf(
                                        KEY_PROGRESS to pct,
                                        KEY_DOWNLOADED to downloaded,
                                        KEY_TOTAL to total,
                                        KEY_SPEED to speed.toLong()
                                    )
                                )
                                setForeground(createForegroundInfo(pct, downloaded, total, isVideo))
                            }
                        }
                    }
                } ?: return@use Result.failure(workDataOf(KEY_ERROR to "storage"))
            }

            // finalize media store entry
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            val item = DownloadItem(
                fileName = fileName,
                mediaStoreUri = uri.toString(),
                sourceUrl = sourceUrl,
                sizeBytes = downloaded
            )
            AppDatabase.get(applicationContext).downloadDao().insert(item)

            showCompletedNotification(fileName)
            Result.success(
                workDataOf(
                    KEY_FILE_URI to uri.toString(),
                    KEY_FILE_NAME to fileName,
                    KEY_DOWNLOADED to downloaded
                )
            )
        } catch (e: IOException) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            Result.failure(workDataOf(KEY_ERROR to "network"))
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            Result.failure(workDataOf(KEY_ERROR to "unknown"))
        }
    }

    private fun buildFileName(title: String?, isVideo: Boolean): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val base = if (!title.isNullOrBlank()) {
            sanitize(title).take(60).ifBlank { "Instagram_${sdf.format(Date())}" }
        } else {
            "Instagram_${sdf.format(Date())}"
        }
        return "$base.${if (isVideo) "mp4" else "jpg"}"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trim('.')

    private fun createForegroundInfo(pct: Int, downloaded: Long, total: Long, isVideo: Boolean): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            action = DownloadCancelReceiver.ACTION_CANCEL
            putExtra(DownloadCancelReceiver.EXTRA_WORK_ID, id.toString())
        }
        val cancelPending = PendingIntent.getBroadcast(
            applicationContext, id.hashCode(), cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaLabel = if (isVideo) "vídeo" else "imagem"
        val text = if (total > 0) "$pct%" else "Baixando $mediaLabel..."
        val notification = NotificationCompat.Builder(applicationContext, InstaSaveApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("InstaSave BR")
            .setContentText(if (total > 0) "Baixando $mediaLabel... $pct%" else "Baixando $mediaLabel...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (total > 0) 100 else 0, pct, total <= 0)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_cancel, "Cancelar", cancelPending)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun showCompletedNotification(fileName: String) {
        val intent = Intent(applicationContext, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, InstaSaveApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("InstaSave BR")
            .setContentText("Download concluído: $fileName")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID + 1, notification)
    }

    companion object {
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_TITLE = "title"
        const val KEY_IS_VIDEO = "is_video"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_ERROR = "error"
        const val NOTIF_ID = 4242
    }
}
