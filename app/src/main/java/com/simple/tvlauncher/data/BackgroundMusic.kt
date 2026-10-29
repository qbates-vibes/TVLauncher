package com.simple.tvlauncher.data

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

/**
 * Plays your music files on a loop while the launcher is on screen, fading in softly.
 * It never interrupts audio from another app: if something is already playing, it stays quiet,
 * and it pauses as soon as another app takes over the audio.
 */
class BackgroundMusic(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change < 0) pause() }
        .build()

    private var player: MediaPlayer? = null
    private var prepared = false
    private var tracks: List<String> = emptyList()
    private var trackIndex = 0
    private var failures = 0
    private var volume = 0f
    private var wantPlaying = false
    private var fade: ValueAnimator? = null

    /** Starts or resumes the playlist at [volume] (0 to 1). Calling it again while playing just changes the volume. */
    fun play(tracks: List<String>, volume: Float) {
        if (tracks.isEmpty() || volume <= 0f) {
            stop()
            return
        }
        if (tracks != this.tracks) {
            stop()
            this.tracks = tracks
            trackIndex = 0
        }
        this.volume = volume
        if (wantPlaying) {
            fade?.cancel()
            player?.setVolume(volume, volume)
            return
        }
        if (audioManager.isMusicActive) return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        wantPlaying = true
        failures = 0
        val current = player
        if (current == null) open(trackIndex) else if (prepared) fadeIn(current)
    }

    fun pause() {
        if (!wantPlaying) return
        wantPlaying = false
        fade?.cancel()
        player?.let { if (prepared && it.isPlaying) it.pause() }
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun stop() {
        pause()
        player?.release()
        player = null
        prepared = false
    }

    private fun open(index: Int) {
        fade?.cancel()
        player?.release()
        prepared = false
        trackIndex = index
        val newPlayer = MediaPlayer().apply {
            setAudioAttributes(attributes)
            isLooping = tracks.size == 1
            setOnPreparedListener {
                prepared = true
                failures = 0
                if (wantPlaying) fadeIn(this)
            }
            setOnCompletionListener { open((trackIndex + 1) % tracks.size) }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Can't play ${tracks[trackIndex]} ($what/$extra)")
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

    /** Moves on to the next file, giving up once every file has failed. */
    private fun skipBrokenTrack() {
        failures++
        if (failures < tracks.size) open((trackIndex + 1) % tracks.size) else stop()
    }

    private fun fadeIn(player: MediaPlayer) {
        fade?.cancel()
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
    }
}
