/*
 * This file contains code derived from the Android Open Source Project (AOSP).
 * The derived code is licensed under the Apache License, Version 2.0.
 * Modifications and additions are licensed under the MIT License.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * The original file was heavily modified to work with hardware buttons and in Kotlin
 * So all not-relevant gestures and parts of code were removed and modified in a way to
 * handle hardware button events only
 */

package com.lmqr.ha9_comp_service

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewConfiguration

class HardwareGestureDetector(
    private val listener: OnGestureListener
) {
    private val handler: Handler = object: Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                LONG_PRESS -> {
                    inLongPress = true
                    listener.onLongPress()
                }

                SINGLE_PRESS -> {
                    if (!isDoubleTapping && !inLongPress) {
                        listener.onSinglePress()
                    }
                }

                else -> {}
            }
        }
    }

    private var isDown = false
    private var inLongPress = false
    private var isDoubleTapping = false
    private var lastUpTime: Long = 0

    interface OnGestureListener {
        fun onSinglePress()
        fun onDoublePress()
        fun onLongPress()
    }

    fun onKeyEvent(action: Int, eventTime: Long) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (eventTime - lastUpTime <= DOUBLE_TAP_DELAY) {
                    isDoubleTapping = true
                    listener.onDoublePress()
                    handler.removeMessages(SINGLE_PRESS)
                } else {
                    isDoubleTapping = false
                }
                isDown = true
                inLongPress = false
                handler.sendEmptyMessageAtTime(LONG_PRESS, eventTime + LONG_PRESS_DELAY)
            }

            MotionEvent.ACTION_UP -> {
                isDown = false
                handler.removeMessages(LONG_PRESS)

                when {
                    inLongPress -> {
                        inLongPress = false
                    }
                    isDoubleTapping-> {
                        isDoubleTapping = false
                    }
                    else -> {
                        handler.sendEmptyMessageAtTime(SINGLE_PRESS, eventTime + DOUBLE_TAP_DELAY)
                    }
                }

                lastUpTime = eventTime
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeMessages(LONG_PRESS)
                handler.removeMessages(SINGLE_PRESS)
                isDown = false
                inLongPress = false
                isDoubleTapping = false
            }

            else -> {}
        }
    }

    companion object {
        private const val LONG_PRESS = 1
        private const val SINGLE_PRESS = 2
        private val DOUBLE_TAP_DELAY = ViewConfiguration.getDoubleTapTimeout().toLong()
        private val LONG_PRESS_DELAY = ViewConfiguration.getLongPressTimeout().toLong()
    }
}