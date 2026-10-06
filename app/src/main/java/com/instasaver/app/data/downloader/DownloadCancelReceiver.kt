package com.instasaver.app.data.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

class DownloadCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        val idStr = intent.getStringExtra(EXTRA_WORK_ID) ?: return
        try {
            WorkManager.getInstance(context).cancelWorkById(UUID.fromString(idStr))
        } catch (_: Exception) { /* ignore */ }
    }

    companion object {
        const val ACTION_CANCEL = "com.instasaver.app.CANCEL_DOWNLOAD"
        const val EXTRA_WORK_ID = "work_id"
    }
}
