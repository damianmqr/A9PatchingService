package com.lmqr.ha9_comp_service

import android.os.Handler
import android.os.Looper
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands
import kotlin.math.max
import kotlin.math.min

enum class TemperatureMode {
    White,
    Night,
    Slider,
    None
}

class TemperatureModeManager(
    mode: TemperatureMode,
    initialBrightness: Float,
    private var commandRunner: CommandRunner
) {
    var isDisabled = false
        set(value) {
            if (value)
                setMode(TemperatureMode.White)
            field = value
            if (!value)
                updateMode()
        }

    var whiteToYellow = 1.0
        set(value) {
            field = min(max(value, 0.0), 1.0)
            if (!isDisabled)
                updateMode()
        }

    private val handler = Handler(Looper.getMainLooper())

    private var currentMode: TemperatureMode = TemperatureMode.None

    var brightness: Float = initialBrightness
        set(v) {
            field = v; setBrightness(); }

    private fun updateMode() {
        when (currentMode) {
            TemperatureMode.Night -> {
                commandRunner.runCommands(
                    arrayOf(
                        Commands.YELLOW_BRIGHTNESS((brightness * maxBrightness).toInt()),
                        Commands.LOCK_WHITE,
                        Commands.WHITE_BRIGHTNESS_ALT(0),
                    )
                )
            }

            TemperatureMode.White, TemperatureMode.None -> {
                commandRunner.runCommands(
                    arrayOf(
                        Commands.UNLOCK_WHITE,
                        Commands.WHITE_BRIGHTNESS((brightness * maxBrightness).toInt()),
                        Commands.YELLOW_BRIGHTNESS(0),
                    )
                )
            }

            TemperatureMode.Slider -> {
                val totalBrightness = (brightness * maxBrightness).toInt()
                val whiteBrightness = (whiteToYellow * totalBrightness).toInt()
                val yellowBrightness = totalBrightness - whiteBrightness
                commandRunner.runCommands(
                    arrayOf(
                        Commands.LOCK_WHITE,
                        Commands.YELLOW_BRIGHTNESS(yellowBrightness),
                        Commands.WHITE_BRIGHTNESS_ALT(whiteBrightness),
                    )
                )
            }
        }
    }

    fun setMode(mode: TemperatureMode) {
        if (currentMode != mode) {
            currentMode = mode
            if (!isDisabled) {
                updateMode()
            }
        }
    }

    private var screenOn: Boolean = false

    fun onScreenChange(isScreenOn: Boolean) {
        screenOn = isScreenOn
        if (currentMode == TemperatureMode.Night || currentMode == TemperatureMode.Slider) {
            if (screenOn)
                setBrightness()
            else
                commandRunner.runCommands(
                    arrayOf(
                        Commands.YELLOW_BRIGHTNESS(0),
                        Commands.WHITE_BRIGHTNESS_ALT(0)
                    )
                )
        }
    }


    private val maxBrightness: Int = 2200
    private val brightnessDelay = 100L
    private var nextBrightnessUpdate = 0L

    private val setBrightnessRunnable: Runnable = Runnable {
        when (currentMode) {
            TemperatureMode.Night -> {
                commandRunner.runCommands(
                    arrayOf(
                        Commands.YELLOW_BRIGHTNESS((brightness * maxBrightness).toInt())
                    )
                )
            }

            TemperatureMode.Slider -> {
                val totalBrightness = (brightness * maxBrightness).toInt()
                val whiteBrightness = (whiteToYellow * totalBrightness).toInt()
                val yellowBrightness = totalBrightness - whiteBrightness
                commandRunner.runCommands(
                    arrayOf(
                        Commands.LOCK_WHITE,
                        Commands.YELLOW_BRIGHTNESS(yellowBrightness),
                        Commands.WHITE_BRIGHTNESS_ALT(whiteBrightness),
                    )
                )
            }

            else -> {}
        }
    }

    private fun setBrightness() {
        if (currentMode == TemperatureMode.Night || currentMode == TemperatureMode.Slider) {
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