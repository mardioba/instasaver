package com.instasaver.app.data.downloader

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Grava os diagnósticos de resolução em arquivo, além do logcat.
 *
 * O logcat rola rápido (o buffer do Android é circular e cheio de ruído de
 * outros apps), então quando o teste acontece fora da janela de captura o
 * diagnóstico se perde. Gravando em arquivo, o resultado de qualquer teste
 * fica disponível para leitura posterior — sem depender de captura em tempo
 * real.
 */
internal object DiagLog {

    private const val TAG = "InstaSave"
    private const val FILE_NAME = "diag.log"
    private const val MAX_BYTES = 256 * 1024L

    @Volatile
    private var appContext: Context? = null

    /** Deve ser chamado uma vez com qualquer contexto da aplicação. */
    fun init(context: Context?) {
        if (context != null && appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun log(message: String) {
        Log.d(TAG, message)
        val f = file() ?: return
        try {
            if (f.length() > MAX_BYTES) f.delete()
            f.appendText("${System.currentTimeMillis()} $message\n")
        } catch (_: Exception) {
            // Diagnóstico nunca pode derrubar o fluxo de download.
        }
    }

    /** Conveniência para quem já tem um contexto à mão. */
    fun log(context: Context?, message: String) {
        init(context)
        log(message)
    }

    fun read(): String = try {
        file()?.takeIf { it.exists() }?.readText() ?: "(nenhum teste registrado ainda)"
    } catch (e: Exception) {
        "erro ao ler diag.log: ${e.message}"
    }

    fun clear() {
        try {
            file()?.delete()
        } catch (_: Exception) {
        }
    }

    private fun file(): File? {
        val ctx = appContext ?: return null
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(dir, FILE_NAME)
    }
}
