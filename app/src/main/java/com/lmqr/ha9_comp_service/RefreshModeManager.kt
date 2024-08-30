package com.lmqr.ha9_comp_service

import android.content.SharedPreferences
import com.lmqr.ha9_comp_service.command_runners.CommandRunner
import com.lmqr.ha9_comp_service.command_runners.Commands

enum class RefreshMode(val command: String, val mode: Int) {
    CLEAR(Commands.SPEED_CLEAR, 0),
    BALANCED(Commands.SPEED_BALANCED, 1),
    SMOOTH(Commands.SPEED_SMOOTH, 2),
    SPEED(Commands.SPEED_FAST, 3);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.mode == value } ?: SMOOTH
    }
}

class RefreshModeManager(
    private val sharedPreferences: SharedPreferences,
    private val commandRunner: CommandRunner,
) {
    var currentMode = RefreshMode.SMOOTH
        private set(v) {
            field = v; applyMode()
        }
    init {
        currentMode = RefreshMode.fromInt(defaultRefreshMode())
    }
    private var currentClassifier = ""

    private fun defaultRefreshMode() = (sharedPreferences.getString("refresh_setting", "2")
        ?.let { Integer.parseInt(it) }
        ?: RefreshMode.SMOOTH) as Int
    fun onAppChange(packageName: String) = packageName.run {
        if (this != currentClassifier) {
            currentClassifier = this
            val tempMode = RefreshMode.fromInt(
                sharedPreferences.getInt(
                    toSharedPreferencesKey(
                        !sharedPreferences.getBoolean(
                            "disable_perapprefresh",
                            false
                        )
                    ),
                    defaultRefreshMode()
                )
            )
            if (tempMode != currentMode) {
                currentMode = tempMode
            }
        }
    }

    fun changeMode(refreshMode: RefreshMode) {
        if (refreshMode != currentMode) {
            currentMode = refreshMode
            sharedPreferences.edit()
                .putInt(
                    currentClassifier.toSharedPreferencesKey(
                        !sharedPreferences.getBoolean(
                            "disable_perapprefresh",
                            false
                        )
                    ), refreshMode.mode
                ).apply()
        }
    }

    fun applyMode() {
        commandRunner.runCommands(
            arrayOf(currentMode.command)
        )
    }
}

private fun String.toSharedPreferencesKey(isPerAppEnabled: Boolean): String {
    if (isPerAppEnabled) {
        if (startsWith("package:"))
            return this
        return "package:$this"
    }
    return "package:None"
}