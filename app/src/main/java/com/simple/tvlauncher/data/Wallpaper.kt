package com.simple.tvlauncher.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * The wallpaper is a picture you copy into the app's own folder with `adb push`,
 * so no storage permission is needed.
 */
object Wallpaper {
    private val FILE_NAMES = listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "wallpaper.webp")

    fun folderPath(context: Context): String = "/sdcard/Android/data/${context.packageName}/files"

    /** Returns the wallpaper file if one exists. Also creates the folder so `adb push` can write to it. */
    fun find(context: Context): File? {
        val dir = context.getExternalFilesDir(null) ?: return null
        return FILE_NAMES.map { File(dir, it) }.firstOrNull { it.isFile }
    }

    /** Decodes the picture, scaled down to roughly screen width to save memory. */
    fun load(file: File, maxWidth: Int = 1920): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }
}
