package com.simple.tvlauncher.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * A video file as the wallpaper: fills the screen (cropping the edges, like a picture), loops, and
 * plays its sound at [volume]. It plays only while the launcher is on screen.
 *
 * It uses a SurfaceView, which hands the video straight to the TV's video layer. The launcher itself
 * draws at 1080p, but this way a 4K video still shows at the panel's full 4K.
 */
@Composable
fun VideoWallpaper(path: String, volume: Float, modifier: Modifier = Modifier) {
    key(path) {
        val context = LocalContext.current
        val video = remember { LoopingVideo(context, path) }
        DisposableEffect(Unit) {
            onDispose { video.release() }
        }
        LaunchedEffect(volume) { video.setVolume(volume) }
        LifecycleResumeEffect(Unit) {
            video.resume()
            onPauseOrDispose { video.pause() }
        }
        // A SurfaceView can't be cropped, so make it bigger than the screen in one direction to cover it.
        BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
            val screenAspect = maxWidth / maxHeight
            val (width, height) = if (video.aspectRatio > screenAspect) {
                maxHeight * video.aspectRatio to maxHeight
            } else {
                maxWidth to maxWidth / video.aspectRatio
            }
            AndroidView(
                factory = { SurfaceView(it).also(video::attach) },
                modifier = Modifier.requiredSize(width, height),
            )
        }
    }
}

private class LoopingVideo(context: Context, private val path: String) {
    private val appContext = context.applicationContext

    /** Width over height, once the video has loaded. */
    var aspectRatio by mutableFloatStateOf(16f / 9f)
        private set

    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        // The launcher's music decides for itself whether to play, so the video doesn't take audio focus.
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ false,
        )
        repeatMode = Player.REPEAT_MODE_ONE
        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Can't play $path", error)
                // Otherwise the screen just stays black. The usual cause is a format the TV can't decode,
                // such as 4K at 60 fps in H.264.
                Toast.makeText(
                    appContext,
                    "This TV can't play ${File(path).name}. Converting it with videowall.sh usually fixes that.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        })
    }

    fun attach(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    fun setVolume(level: Float) {
        player.volume = level
        // Muted drops the sound track entirely, so the TV doesn't count the video as playing audio
        // and the background music can still play.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, level <= 0f)
            .build()
    }

    fun resume() {
        player.prepare()
        player.play()
    }

    /**
     * Stops and frees the video decoder, so an app opened from the launcher can use it.
     * The position is kept, so [resume] carries on from the same spot.
     */
    fun pause() {
        player.stop()
    }

    fun release() {
        player.release()
    }

    private companion object {
        const val TAG = "VideoWallpaper"
    }
}
