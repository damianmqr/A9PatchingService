package com.lmqr.ha9_comp_service

import android.os.Handler
import android.os.Looper

enum class TemperatureMode{
    White,
    Night,
    None
}

class TemperatureModeManager{

    private val handler = Handler(Looper.getMainLooper())

    private var currentMode: TemperatureMode = TemperatureMode.None
    private lateinit var rootCommandRunner: RootCommandRunner

    var brightness: Float = 0.0f
        get() = field
        set(v) { field = v; setBrightness(); }

    constructor(mode: TemperatureMode, rootCommandRunner: RootCommandRunner){
        this.rootCommandRunner = rootCommandRunner
        setMode(mode)
    }
    fun setMode(mode: TemperatureMode){
        if(currentMode != mode) {
            currentMode = mode
            when(mode){
                TemperatureMode.Night -> {
                    rootCommandRunner.runAsRoot(arrayOf(
                        "echo 0 > /sys/class/leds/aw99703-bl-2/brightness",
                        "chmod 644 /sys/class/leds/aw99703-bl-1/brightness",
                        "chmod 444 /sys/class/leds/aw99703-bl-2/brightness",
                        "echo ${(brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-1/brightness"
                        ))
                }
                TemperatureMode.White, TemperatureMode.None -> {
                    rootCommandRunner.runAsRoot(arrayOf(
                        "chmod 644 /sys/class/leds/aw99703-bl-2/brightness",
                        "echo 0 > /sys/class/leds/aw99703-bl-1/brightness",
                        "echo ${(brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-2/brightness",
                    ))
                }
            }
        }
    }

    private var screenOn: Boolean = false

    fun onScreenChange(isScreenOn: Boolean){
        screenOn = isScreenOn
        if (BuildConfig.USE_TEMPERATURE && currentMode == TemperatureMode.Night){
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
        if (BuildConfig.USE_TEMPERATURE && currentMode == TemperatureMode.Night) {
            rootCommandRunner.runAsRoot(
                arrayOf(
                    "echo ${(brightness * maxBrightness).toInt()} > /sys/class/leds/aw99703-bl-1/brightness"
                )
            )
        }
    }

    private fun setBrightness(){
        if(BuildConfig.USE_TEMPERATURE && currentMode == TemperatureMode.Night) {
            val currentTime = System.currentTimeMillis()
            if (nextBrightnessUpdate < currentTime) {
                nextBrightnessUpdate = currentTime + brightnessDelay
                handler.postDelayed(setBrightnessRunnable, brightnessDelay + 10L)
            }
        }
    }

}