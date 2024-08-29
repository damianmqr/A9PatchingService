package com.lmqr.ha9_comp_service

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import kotlin.math.min

enum class AODOpacity(val mode: Int) {
    OPAQUE( 0),
    SEMIOPAQUE(1),
    SEMICLEAR( 2),
    CLEAR(3),
    NOTSET(4);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.mode == value } ?: OPAQUE
    }
}

class StaticAODOpacityManager(
    private val sharedPreferences: SharedPreferences,
    private val commandRunner: CommandRunner,
    private val iconOpacityArray: IntArray,
    private val backgroundOpacityArray: IntArray,
) {
    var currentOpacity = AODOpacity.NOTSET
        private set(v) {
            field = v; applyMode()
        }
    private var currentClassifier = ""

    fun onAppChange(packageName: String) = packageName.run {
        if (this != currentClassifier) {
            currentClassifier = this
            val tempMode = AODOpacity.fromInt(
                sharedPreferences.getInt(
                    toSharedPreferencesKey(),
                    AODOpacity.NOTSET.mode
                )
            )
            if (tempMode != currentOpacity) {
                currentOpacity = tempMode
            }
        }
    }

    fun changeMode(opacity: AODOpacity) {
        if (opacity != currentOpacity) {
            currentOpacity = opacity
            sharedPreferences.edit()
                .putInt(
                    currentClassifier.toSharedPreferencesKey(), opacity.mode
                ).apply()
        }
    }

    fun applyMode() {
        try{
            sharedPreferences.run {
                val op =
                    if(currentOpacity == AODOpacity.NOTSET)
                        Integer.parseInt(getString("static_lockscreen_opacity", "0") ?: "0")
                    else
                        iconOpacityArray[min(currentOpacity.mode, iconOpacityArray.size-1)]
                val tp = Integer.parseInt(getString("static_lockscreen_type", "0") ?: "0")
                val bgop =
                    if(currentOpacity == AODOpacity.NOTSET)
                        Integer.parseInt(getString("static_lockscreen_bg_opacity", "0") ?: "0")
                    else
                        backgroundOpacityArray[min(currentOpacity.mode, iconOpacityArray.size-1)]
                val mix = Integer.parseInt(getString("static_lockscreen_mix_color", "0") ?: "0")
                commandRunner.runCommands(arrayOf("stl${op + tp + mix + bgop}"))
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
    }
}

private fun String.toSharedPreferencesKey(): String {
    if (startsWith("package:"))
            return this
        return "package:$this"
}