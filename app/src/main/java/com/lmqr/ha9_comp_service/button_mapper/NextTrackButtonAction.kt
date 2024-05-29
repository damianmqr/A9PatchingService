package com.lmqr.ha9_comp_service.button_mapper

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

class NextTrackButtonAction : ButtonAction {
    override fun execute(context: Context) {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?)?.run {
            SystemClock.uptimeMillis().let { time ->
                dispatchMediaKeyEvent(
                    KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, 0)
                )
            }

            SystemClock.uptimeMillis().let { time ->
                dispatchMediaKeyEvent(
                    KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT, 0)
                )
            }
        }
    }
}