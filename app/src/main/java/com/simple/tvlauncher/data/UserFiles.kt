package com.simple.tvlauncher.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** A picture or video you can choose as the wallpaper, with a small preview for the settings screen. */
data class WallpaperOption(val name: String, val thumbnail: ImageBitmap?, val isVideo: Boolean)

/**
 * Pictures and music you copy onto the TV with `adb push`. They live in the app's own folder,
 * so no storage permission is needed and nothing is downloaded:
 *   files/          wallpaper pictures and videos
 *   files/logo/     a logo picture for the hero select screen
 *   files/music/    background music
 */
object UserFiles {
    const val LOGO_FOLDER = "logo"
    const val MUSIC_FOLDER = "music"
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav")
    val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp")
    val WALLPAPER_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    fun isVideo(file: File): Boolean = file.extension.lowercase() in VIDEO_EXTENSIONS

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

    /**
     * A video's thumbnail: the picture embedded in it if there is one (videowall.sh adds one, since
     * the TV can't decode 4K frames for thumbnails), otherwise a frame from about a second in.
     */
    fun videoThumbnail(file: File, maxWidth: Int = 320): ImageBitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val embedded = retriever.embeddedPicture?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sampleSize = 1
                while (bounds.outWidth / (sampleSize * 2) >= maxWidth) sampleSize *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
            (embedded ?: retriever.getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxWidth, maxWidth))
                ?.asImageBitmap()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
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
