package com.lifemanga.android.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

object ImageCompression {

    private const val TARGET_BYTES = 500 * 1024
    private const val INITIAL_QUALITY = 85
    private const val FLOOR_QUALITY = 40
    private const val SHRINK_FACTOR = 0.85
    private const val MAX_PASSES = 6

    fun compressFileToJpeg(path: String): ByteArray? {
        val raw = BitmapFactory.decodeFile(path) ?: return null
        return try {
            compressBitmap(raw)
        } finally {
            raw.recycle()
        }
    }

    fun compressBitmap(source: Bitmap): ByteArray {
        var bitmap = source
        var quality = INITIAL_QUALITY
        var pass = 0
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= TARGET_BYTES || pass >= MAX_PASSES) {
                if (bitmap !== source) bitmap.recycle()
                return bytes
            }
            pass++
            if (quality > FLOOR_QUALITY) {
                quality = (quality - 10).coerceAtLeast(FLOOR_QUALITY)
                continue
            }
            val newW = (bitmap.width * SHRINK_FACTOR).toInt().coerceAtLeast(256)
            val newH = (bitmap.height * SHRINK_FACTOR).toInt().coerceAtLeast(256)
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            if (bitmap !== source) bitmap.recycle()
            bitmap = scaled
        }
    }

    fun ensureUnder(file: File, target: Int = TARGET_BYTES): ByteArray? {
        if (file.length() <= target) return file.readBytes()
        return compressFileToJpeg(file.absolutePath)
    }
}
