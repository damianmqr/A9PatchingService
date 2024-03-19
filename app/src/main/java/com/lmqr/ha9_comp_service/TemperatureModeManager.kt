package com.lmqr.ha9_comp_service

import android.os.Handler
import android.os.Looper
import kotlin.math.min

enum class TemperatureMode{
    White,
    Night,
    None
}

class TemperatureModeManager(
    mode: TemperatureMode,
    initialBrightness: Float,
    private var rootCommandRunner: RootCommandRunner
) {

    private val handler = Handler(Looper.getMainLooper())

    private var currentMode: TemperatureMode = TemperatureMode.None

    var brightness: Float = initialBrightness
        set(v) { field = v; setBrightness(); }

    fun setMode(mode: TemperatureMode){
        if(currentMode != mode) {
            currentMode = mode
            when(mode){
                TemperatureMode.Night -> {
                    rootCommandRunner.runAsRoot(arrayOf(
                        "echo ${(brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-1/brightness",
                        "echo 0 > /sys/class/leds/aw99703-bl-2/brightness",
                        "chmod 444 /sys/class/leds/aw99703-bl-2/brightness",
                        ))
                }
                TemperatureMode.White, TemperatureMode.None -> {
                    rootCommandRunner.runAsRoot(arrayOf(
                        "chmod 644 /sys/class/leds/aw99703-bl-2/brightness",
                        "echo ${(brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-2/brightness",
                        "echo 0 > /sys/class/leds/aw99703-bl-1/brightness",
                        "settings put system screen_brightness \$(settings get system screen_brightness)",
                    ))
                }
            }
        }
    }

    private var screenOn: Boolean = false

    fun onScreenChange(isScreenOn: Boolean){
        screenOn = isScreenOn
        if (currentMode == TemperatureMode.Night){
            if(screenOn)
                setBrightness()
            else
                rootCommandRunner.runAsRoot(arrayOf("echo 0 > /sys/class/leds/aw99703-bl-1/brightness"))
        }
    }


    private val maxBrightness: Int = 2400
    private val brightnessDelay = 100L
    private var nextBrightnessUpdate = 0L

    private val setBrightnessRunnable: Runnable = Runnable {
        if (currentMode == TemperatureMode.Night) {
            rootCommandRunner.runAsRoot(
                arrayOf(
                    "echo ${(brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-1/brightness"
                )
            )
        }
    }

    private fun setBrightness(){
        if(currentMode == TemperatureMode.Night) {
            val currentTime = System.currentTimeMillis()
            if (nextBrightnessUpdate < currentTime) {
                nextBrightnessUpdate = currentTime + brightnessDelay
                handler.postDelayed(setBrightnessRunnable, brightnessDelay + 10L)
            }
        }
    }

    init {
        setMode(mode)
    }

}