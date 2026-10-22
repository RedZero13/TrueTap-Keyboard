package com.redzero.keyboard.net

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.security.MessageDigest

/** Loads (animated) images from URLs into ImageViews, with disk and memory caching. */
class ImageLoader(context: Context) {
    private val dir = File(context.cacheDir, "img").apply { mkdirs() }
    private val memory = LruCache<String, Drawable>(40)

    fun fileFor(url: String): File = File(dir, hash(url))

    fun load(url: String, into: ImageView, maxSidePx: Int) {
        into.tag = url
        memory.get(url)?.let { show(into, it); return }
        into.setImageDrawable(null)
        Net.async({
            val f = Net.download(url, fileFor(url))
            val src = ImageDecoder.createSource(f)
            ImageDecoder.decodeDrawable(src) { decoder, info, _ ->
                val s = info.size
                val scale = maxSidePx.toFloat() / maxOf(s.width, s.height)
                if (scale < 1f) decoder.setTargetSize((s.width * scale).toInt().coerceAtLeast(1), (s.height * scale).toInt().coerceAtLeast(1))
            }
        }) { r ->
            val d = r.getOrNull() ?: return@async
            memory.put(url, d)
            if (into.tag == url) show(into, d)
        }
    }

    private fun show(v: ImageView, d: Drawable) {
        v.setImageDrawable(d)
        if (d is AnimatedImageDrawable) d.start()
    }

    fun clearMemory() = memory.evictAll()

    companion object {
        fun hash(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
