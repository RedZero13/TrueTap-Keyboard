package com.redzero.keyboard.net

import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Tiny HTTP helpers on a shared background pool; callbacks run on the main thread. */
object Net {
    val pool = Executors.newFixedThreadPool(4) { r -> Thread(r, "redzero-net").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    fun <T> async(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute {
            val r = runCatching(work)
            main.post { done(r) }
        }
    }

    fun onMain(block: () -> Unit) {
        main.post(block)
    }

    fun getText(url: String): String {
        val c = open(url)
        try {
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code: ${body.take(200)}")
            return body
        } finally {
            c.disconnect()
        }
    }

    /** Download to [dest] (atomically via a temp file) unless it already exists. */
    fun download(url: String, dest: File): File {
        if (dest.exists() && dest.length() > 0) return dest
        dest.parentFile?.mkdirs()
        val tmp = File(dest.path + ".part")
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
        if (!tmp.renameTo(dest)) throw IOException("rename failed")
        return dest
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "RedZeroKeyboard/0.1 (Android)")
        return c
    }

    fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
