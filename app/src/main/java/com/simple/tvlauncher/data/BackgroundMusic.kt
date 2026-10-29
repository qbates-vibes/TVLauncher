package com.simple.tvlauncher.data

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Plays your music files while the launcher is on screen. Files play once each in A–Z order and
 * the last one loops, so a short intro can lead into a long theme. Every time the launcher comes
 * back on screen, the music starts over from the first file with a soft fade-in.
 *
 * It never interrupts audio from another app: if something is already playing, it stays quiet,
 * and it stops as soon as another app takes over the audio.
 */
class BackgroundMusic(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
        .build()

    private var player: MediaPlayer? = null
    private var tracks: List<String> = emptyList()
    private var trackIndex = 0
    private var volume = 0f
    private var playing = false
    private var fade: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retry: Runnable? = null

    /** Starts the music from the first file at [volume] (0 to 1). Calling it again while playing just changes the volume. */
    fun play(tracks: List<String>, volume: Float) {
        if (tracks.isEmpty() || volume <= 0f) {
            stop()
            return
        }
        this.volume = volume
        if (playing && tracks == this.tracks) {
            fade?.cancel()
            player?.setVolume(volume, volume)
            return
        }
        stop()
        start(tracks, attemptsLeft = START_ATTEMPTS)
    }

    /**
     * Starts unless another app is playing audio. Apps you just left (or the stock home screen's
     * trailers) can take a moment to go quiet, so keep checking for a couple of seconds first.
     */
    private fun start(tracks: List<String>, attemptsLeft: Int) {
        if (audioManager.isMusicActive) {
            if (attemptsLeft > 1) {
                retry = Runnable { start(tracks, attemptsLeft - 1) }.also { handler.postDelayed(it, RETRY_DELAY_MS) }
            }
            return
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        this.tracks = tracks
        playing = true
        open(0)
    }

    /** Stops and forgets the position, so the next [play] starts from the beginning. */
    fun stop() {
        retry?.let { handler.removeCallbacks(it) }
        retry = null
        if (playing) audioManager.abandonAudioFocusRequest(focusRequest)
        playing = false
        fade?.cancel()
        player?.release()
        player = null
    }

    private fun open(index: Int) {
        fade?.cancel()
        player?.release()
        trackIndex = index
        val newPlayer = MediaPlayer().apply {
            setAudioAttributes(attributes)
            isLooping = index == tracks.lastIndex
            setOnPreparedListener {
                if (!playing) return@setOnPreparedListener
                // Fade in only at the very start; later files follow straight on.
                if (index == 0) {
                    fadeIn(this)
                } else {
                    setVolume(volume, volume)
                    start()
                }
            }
            setOnCompletionListener { open(index + 1) }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Can't play ${tracks[index]} ($what/$extra)")
                skipBrokenTrack()
                true
            }
        }
        player = newPlayer
        try {
            newPlayer.setDataSource(tracks[index])
            newPlayer.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Can't open ${tracks[index]}", e)
            skipBrokenTrack()
        }
    }

    /** Moves on to the next file, or stops if the broken one was the last. */
    private fun skipBrokenTrack() {
        if (trackIndex < tracks.lastIndex) open(trackIndex + 1) else stop()
    }

    private fun fadeIn(player: MediaPlayer) {
        player.setVolume(0f, 0f)
        player.start()
        fade = ValueAnimator.ofFloat(0f, volume).apply {
            duration = FADE_IN_MS
            addUpdateListener {
                val v = it.animatedValue as Float
                player.setVolume(v, v)
            }
            start()
        }
    }

    private companion object {
        const val TAG = "BackgroundMusic"
        const val FADE_IN_MS = 2500L
        const val START_ATTEMPTS = 6
        const val RETRY_DELAY_MS = 500L
    }
}
