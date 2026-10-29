package com.simple.tvlauncher.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** A picture you can choose as the wallpaper, with a small preview for the settings screen. */
data class WallpaperOption(val name: String, val thumbnail: ImageBitmap?)

/**
 * Wallpapers are pictures you copy into the app's own folder with `adb push`,
 * so no storage permission is needed.
 */
object Wallpaper {
    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun folderPath(context: Context): String = "/sdcard/Android/data/${context.packageName}/files"

    /** Every picture in the folder, A–Z. Also creates the folder so `adb push` can write to it. */
    fun list(context: Context): List<File> {
        val dir = context.getExternalFilesDir(null) ?: return emptyList()
        return dir.listFiles { file -> file.isFile && file.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /** Decodes the picture, scaled down to roughly [maxWidth] pixels wide to save memory. */
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
