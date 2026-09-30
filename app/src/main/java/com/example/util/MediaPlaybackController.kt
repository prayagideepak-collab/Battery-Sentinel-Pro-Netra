package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages media audio focus coordination for voice announcements:
 * - Detects active media playback before an announcement.
 * - Requests transient audio focus to cleanly pause playing media.
 * - On announcement completion, abandons focus to restore playback.
 * - Crucially: if media was already paused before the announcement, it will NEVER be resumed.
 */
class MediaPlaybackController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasMediaPlayingBeforeAnnouncement: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: $focusChange")
    }

    /**
     * Checks if media is currently playing on the device.
     */
    fun isMediaPlaying(): Boolean {
        return try {
            audioManager?.isMusicActive == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Called immediately before Text-to-Speech starts.
     * If media was actively playing, acquires transient audio focus to pause third-party media.
     * Returns true if media was playing prior to announcement.
     */
    @Synchronized
    fun prepareForAnnouncement(): Boolean {
        wasMediaPlayingBeforeAnnouncement = isMediaPlaying()
        Log.d(TAG, "prepareForAnnouncement - wasMediaPlaying: $wasMediaPlayingBeforeAnnouncement")

        if (wasMediaPlayingBeforeAnnouncement) {
            requestTransientFocus()
        }

        return wasMediaPlayingBeforeAnnouncement
    }

    /**
     * Called immediately when Text-to-Speech finishes speaking or is cancelled.
     * Restores media playback ONLY if it was actively playing before the announcement.
     */
    @Synchronized
    fun restoreAfterAnnouncement() {
        Log.d(TAG, "restoreAfterAnnouncement - wasMediaPlaying: $wasMediaPlayingBeforeAnnouncement")
        if (wasMediaPlayingBeforeAnnouncement) {
            abandonTransientFocus()
        }
        wasMediaPlayingBeforeAnnouncement = false
    }

    private fun requestTransientFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()

                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request transient audio focus", e)
        }
    }

    private fun abandonTransientFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { req ->
                    am.abandonAudioFocusRequest(req)
                }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon transient audio focus", e)
        }
    }

    companion object {
        private const val TAG = "MediaPlaybackCtrl"
    }
}
