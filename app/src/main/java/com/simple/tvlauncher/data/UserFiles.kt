package com.simple.tvlauncher.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** A picture you can choose as the wallpaper, with a small preview for the settings screen. */
data class WallpaperOption(val name: String, val thumbnail: ImageBitmap?)

/**
 * Pictures and music you copy onto the TV with `adb push`. They live in the app's own folder,
 * so no storage permission is needed and nothing is downloaded:
 *   files/          wallpaper pictures
 *   files/logo/     a logo picture for the hero select screen
 *   files/music/    background music
 */
object UserFiles {
    const val LOGO_FOLDER = "logo"
    const val MUSIC_FOLDER = "music"
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")

    fun folderPath(context: Context, subfolder: String? = null): String =
        "/sdcard/Android/data/${context.packageName}/files" + (subfolder?.let { "/$it" } ?: "")

    /**
     * Files in [subfolder] (or the top folder) with one of [extensions], A–Z.
     * Also creates the folder so `adb push` can write to it.
     */
    fun list(context: Context, subfolder: String?, extensions: Set<String>): List<File> {
        val root = context.getExternalFilesDir(null) ?: return emptyList()
        val dir = if (subfolder == null) root else File(root, subfolder).apply { mkdirs() }
        return dir.listFiles { file -> file.isFile && file.extension.lowercase() in extensions }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /** Decodes a picture, scaled down to roughly [maxWidth] pixels wide to save memory. */
    fun loadImage(file: File, maxWidth: Int = 1920): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }
}
