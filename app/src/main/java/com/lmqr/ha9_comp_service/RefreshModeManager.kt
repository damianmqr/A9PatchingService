package com.lmqr.ha9_comp_service

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner

enum class RefreshMode(val mode: Int){
    CLEAR(0),
    BALANCED(1),
    SMOOTH(2),
    SPEED(3),
    NONE(4);

    companion object {
        fun fromInt(value: Int) = entries.first { it.mode == value }
    }
}

class RefreshModeManager(
    private val sharedPreferences: SharedPreferences,
    private val commandRunner: CommandRunner,
) {
    var currentMode = RefreshMode.NONE
        private set(v) { field = v; applyMode() }
    private var currentClassifier = ""
    fun onAppChange(packageName: String) = packageName.run{
        if(this != currentClassifier){
            currentClassifier = this
            val tempMode = RefreshMode.fromInt(
                sharedPreferences.getInt(
                    toSharedPreferencesKey(!sharedPreferences.getBoolean("disable_perapprefresh", false)),
                    sharedPreferences.getString("refresh_setting", "2")
                        ?.let { Integer.parseInt(it) }
                        ?: 2
                )
            )
            if(tempMode != currentMode) {
                currentMode = tempMode
            }
        }
    }

    fun changeMode(refreshMode: RefreshMode){
        if(refreshMode != currentMode) {
            currentMode = refreshMode
            sharedPreferences.edit()
                .putInt(currentClassifier.toSharedPreferencesKey(!sharedPreferences.getBoolean("disable_perapprefresh", false)), refreshMode.mode).apply()
        }
    }

    private fun applyMode() = when(currentMode){
        RefreshMode.CLEAR -> commandRunner.runCommands(arrayOf("c"))
        RefreshMode.BALANCED -> commandRunner.runCommands(arrayOf("b"))
        RefreshMode.SMOOTH -> commandRunner.runCommands(arrayOf("s"))
        RefreshMode.SPEED -> commandRunner.runCommands(arrayOf("p"))
        RefreshMode.NONE -> {}
    }
}

private fun String.toSharedPreferencesKey(isPerAppEnabled: Boolean): String {
    if(isPerAppEnabled) {
        if (startsWith("package:"))
            return this
        return "package:$this"
    }
    return "package:None"
}