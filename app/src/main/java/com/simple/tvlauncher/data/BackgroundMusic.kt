package com.simple.tvlauncher.data

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Plays your music files while the launcher is on screen. The first time the launcher shows after
 * the TV wakes up, files play once each in A–Z order and the last one loops, so a short intro can
 * lead into a long theme. Later visits skip the intro: the theme alone fades in after 30 seconds.
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
    private var firstIndex = 0
    private var volume = 0f
    private var playing = false
    private var fade: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    /** A delayed start: the wait before the theme, or another check that other audio has gone quiet. */
    private var pending: Runnable? = null

    init {
        watchForSleep(context.applicationContext)
    }

    /**
     * Starts the music at [volume] (0 to 1): from the first file if the TV has slept since the intro
     * last played, otherwise just the last file after a pause. Calling it again while playing (or
     * waiting to) just changes the volume.
     */
    fun play(tracks: List<String>, volume: Float) {
        if (tracks.isEmpty() || volume <= 0f) {
            stop()
            return
        }
        this.volume = volume
        if ((playing || pending != null) && tracks == this.tracks) {
            fade?.cancel()
            player?.setVolume(volume, volume)
            return
        }
        stop()
        this.tracks = tracks
        if (introDue) {
            start(from = 0, attemptsLeft = START_ATTEMPTS)
        } else {
            schedule(THEME_DELAY_MS) { start(from = tracks.lastIndex, attemptsLeft = START_ATTEMPTS) }
        }
    }

    /**
     * Starts unless another app is playing audio. Apps you just left (or the stock home screen's
     * trailers) can take a moment to go quiet, so keep checking for a couple of seconds first.
     */
    private fun start(from: Int, attemptsLeft: Int) {
        pending = null
        if (audioManager.isMusicActive) {
            if (attemptsLeft > 1) schedule(RETRY_DELAY_MS) { start(from, attemptsLeft - 1) }
            return
        }
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        playing = true
        if (from == 0) introDue = false
        firstIndex = from
        open(from)
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        pending = Runnable { action() }.also { handler.postDelayed(it, delayMs) }
    }

    /** Stops and forgets the position, so the next [play] starts over (from the intro only if the TV has slept). */
    fun stop() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
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
                if (index == firstIndex) {
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
        const val THEME_DELAY_MS = 30_000L

        /**
         * Whether the next start plays the intro. Kept for the whole app process, so it survives the
         * home screen being rebuilt; a fresh process (after a reboot, say) starts with it due.
         */
        var introDue = true
        var watchingSleep = false

        /** Turning the TV off with the remote turns the screen off, which makes the intro due again. */
        fun watchForSleep(context: Context) {
            if (watchingSleep) return
            watchingSleep = true
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    introDue = true
                }
            }
            // A system broadcast; the receiver only re-arms the intro.
            ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
        }
    }
}
