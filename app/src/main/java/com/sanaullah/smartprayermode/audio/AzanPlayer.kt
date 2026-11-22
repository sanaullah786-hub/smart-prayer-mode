package com.sanaullah.smartprayermode.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

enum class AzanSource(val label: String, val assetPath: String?, val note: String) {
    MADINAH("Madinah (Masjid an-Nabawi style)", "azan/madinah.mp3", "Licensed recording must be supplied"),
    MAKKAH("Makkah (Masjid al-Haram style)", "azan/makkah.mp3", "Licensed recording must be supplied"),
    EGYPTIAN("Egyptian", "azan/egyptian.mp3", "Licensed recording must be supplied"),
    TURKISH("Turkish", "azan/turkish.mp3", "Licensed recording must be supplied"),
    CUSTOM("Custom (your own audio file)", null, "Choose an audio file from your device");

    companion object {
        fun fromName(name: String?): AzanSource = entries.firstOrNull { it.name == name } ?: MADINAH
    }
}

object AzanLibrary {
    /** True when the bundled asset exists (or, for CUSTOM, when a file has been chosen). */
    fun isAvailable(context: Context, source: AzanSource, customUri: String?): Boolean =
        if (source == AzanSource.CUSTOM) {
            !customUri.isNullOrBlank()
        } else {
            runCatching { context.assets.openFd(source.assetPath!!).close() }.isSuccess
        }
}

/**
 * Single shared Azan player (preview, test, scheduled playback).
 * All calls must be made on the main thread.
 */
class AzanPlayer(context: Context) {

    enum class State { IDLE, PLAYING, PAUSED }

    data class UiState(
        val state: State = State.IDLE,
        val label: String = "",
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val message: String? = null,
    )

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(UiState())
    val ui: kotlinx.coroutines.flow.StateFlow<UiState> = _ui

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var volume = 0.8f
    private var resumeOnGain = false
    private var onFinished: (() -> Unit)? = null
    private var fallbackRingtone: android.media.Ringtone? = null

    val isActive: Boolean get() = player != null || fallbackRingtone?.isPlaying == true

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            runCatching {
                _ui.value = _ui.value.copy(positionMs = p.currentPosition, durationMs = p.duration)
            }
            if (_ui.value.state == State.PLAYING) handler.postDelayed(this, 500)
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val p = player ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> finish()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (p.isPlaying) {
                resumeOnGain = true
                p.pause()
                _ui.value = _ui.value.copy(state = State.PAUSED)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> p.setVolume(volume * 0.3f, volume * 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                p.setVolume(volume, volume)
                if (resumeOnGain) {
                    resumeOnGain = false
                    p.start()
                    _ui.value = _ui.value.copy(state = State.PLAYING)
                    handler.post(ticker)
                }
            }
        }
    }

    /**
     * @param useFallbackTone when the Azan file is missing, play the device notification
     * tone so the prayer time is still audible (never presented as an Azan).
     */
    fun play(
        source: AzanSource,
        customUri: String?,
        volume: Float,
        useFallbackTone: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        stop()
        this.volume = volume.coerceIn(0f, 1f)
        this.onFinished = onFinished

        if (!AzanLibrary.isAvailable(appContext, source, customUri)) {
            val msg = if (source == AzanSource.CUSTOM) {
                "No custom audio file selected."
            } else {
                "Azan file for \"${source.label}\" has not been added (see assets/azan/README.md)."
            }
            _ui.value = UiState(message = msg)
            if (useFallbackTone) playFallbackTone() else finish()
            return
        }

        if (!requestFocus()) {
            _ui.value = UiState(message = "Another app is using the audio output.")
            finish()
            return
        }

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attributes)
            if (source == AzanSource.CUSTOM) {
                mp.setDataSource(appContext, Uri.parse(customUri))
            } else {
                appContext.assets.openFd(source.assetPath!!).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            }
            mp.setOnPreparedListener {
                it.setVolume(this.volume, this.volume)
                it.start()
                _ui.value = UiState(State.PLAYING, source.label, 0, it.duration, null)
                handler.post(ticker)
            }
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error $what/$extra")
                _ui.value = UiState(message = "Could not play this audio file.")
                finish()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Azan", e)
            _ui.value = UiState(message = "Could not play this audio file.")
            finish()
        }
    }

    fun togglePause() {
        val p = player ?: return
        runCatching {
            if (p.isPlaying) {
                p.pause()
                _ui.value = _ui.value.copy(state = State.PAUSED)
            } else {
                p.start()
                _ui.value = _ui.value.copy(state = State.PLAYING)
                handler.post(ticker)
            }
        }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        runCatching { player?.setVolume(volume, volume) }
    }

    /** Stops playback and releases everything without invoking the completion callback. */
    fun stop() {
        onFinished = null
        release()
        _ui.value = UiState()
    }

    private fun finish() {
        val cb = onFinished
        onFinished = null
        release()
        _ui.value = UiState(message = _ui.value.message)
        cb?.invoke()
    }

    private fun release() {
        handler.removeCallbacks(ticker)
        resumeOnGain = false
        runCatching { fallbackRingtone?.stop() }
        fallbackRingtone = null
        player?.let {
            runCatching { it.setOnCompletionListener(null); it.setOnErrorListener(null); it.stop() }
            runCatching { it.release() }
        }
        player = null
        abandonFocus()
    }

    private fun playFallbackTone() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val tone = RingtoneManager.getRingtone(appContext, uri)
        if (tone == null) {
            finish()
            return
        }
        tone.audioAttributes = attributes
        fallbackRingtone = tone
        tone.play()
        handler.postDelayed({ finish() }, 4_000)
    }

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val TAG = "AzanPlayer"
    }
}
