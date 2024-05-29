package com.lmqr.ha9_comp_service.button_mapper

import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent


class TogglePauseButtonAction : ButtonAction {
    override fun execute(context: Context) {
        (context.getSystemService(AUDIO_SERVICE) as AudioManager?)?.run {
            val event = if (isMusicActive)
                KeyEvent.KEYCODE_MEDIA_PAUSE
            else
                KeyEvent.KEYCODE_MEDIA_PLAY

            SystemClock.uptimeMillis().let { time ->
                dispatchMediaKeyEvent(
                    KeyEvent(time, time, KeyEvent.ACTION_DOWN, event, 0)
                )
            }

            SystemClock.uptimeMillis().let { time ->
                dispatchMediaKeyEvent(
                    KeyEvent(time, time, KeyEvent.ACTION_UP, event, 0)
                )
            }
        }
    }
}