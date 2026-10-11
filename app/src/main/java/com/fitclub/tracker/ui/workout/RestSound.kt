package com.fitclub.tracker.ui.workout

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * End-of-rest signal: two short beeps on the media stream, so it is heard in headphones and
 * follows the media volume rather than the ringer, which is often muted in the gym.
 */
object RestSound {
    private const val VOLUME = 90
    private const val LENGTH_MS = 600

    fun play() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, LENGTH_MS)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { tone.release() } }, LENGTH_MS + 200L)
        }
    }
}
