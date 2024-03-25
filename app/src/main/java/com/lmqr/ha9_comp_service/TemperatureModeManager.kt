package com.lmqr.ha9_comp_service

import android.os.Handler
import android.os.Looper
import com.lmqr.ha9_comp_service.command_runners.CommandRunner

enum class TemperatureMode{
    White,
    Night,
    None
}

class TemperatureModeManager(
    mode: TemperatureMode,
    initialBrightness: Float,
    private var commandRunner: CommandRunner
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
                    commandRunner.runCommands(arrayOf(
                        "sb1${(brightness * maxBrightness).toInt()}",
                        "sb20",
                        "bl",
                    ))
                }
                TemperatureMode.White, TemperatureMode.None -> {
                    commandRunner.runCommands(arrayOf(
                        "un",
                        "sb2${(brightness * maxBrightness).toInt()}",
                        "sb10",
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
                commandRunner.runCommands(arrayOf("sb10"))
        }
    }


    private val maxBrightness: Int = 2200
    private val brightnessDelay = 100L
    private var nextBrightnessUpdate = 0L

    private val setBrightnessRunnable: Runnable = Runnable {
        if (currentMode == TemperatureMode.Night) {
            commandRunner.runCommands(arrayOf("sb1${(brightness * maxBrightness).toInt()}"))
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