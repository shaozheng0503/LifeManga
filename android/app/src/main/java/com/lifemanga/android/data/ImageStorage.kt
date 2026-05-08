package com.lifemanga.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageStorage(private val context: Context) {

    private val baseDir: File = File(context.filesDir, "manga_images").apply { mkdirs() }

    fun saveBytes(bytes: ByteArray, suffix: String = ".png"): String {
        val file = File(baseDir, "${UUID.randomUUID()}$suffix")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun saveUriAsJpeg(uri: Uri, maxSide: Int = 1600, quality: Int = 90): String? {
        val bitmap = decodeUri(uri, maxSide) ?: return null
        val file = File(baseDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
        return file.absolutePath
    }

    fun decodeFile(path: String?, maxSide: Int = 2000): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun decodeUri(uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    fun captureFile(): File {
        val cacheDir = File(context.cacheDir, "capture").apply { mkdirs() }
        return File(cacheDir, "${UUID.randomUUID()}.jpg")
    }
}
