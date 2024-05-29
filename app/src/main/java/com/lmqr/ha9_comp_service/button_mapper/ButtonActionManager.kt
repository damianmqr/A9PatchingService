package com.lmqr.ha9_comp_service.button_mapper

import android.content.Context
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.command_runners.CommandRunner

class ButtonActionManager(commandRunner: CommandRunner) {
    private val buttonCommands = mapOf(
        "clear" to ClearScreenButtonAction(commandRunner),
        "next_track" to NextTrackButtonAction(),
        "toggle_music" to TogglePauseButtonAction(),
        "back" to BackButtonAction(),
        "open_menu" to ToggleMenuButtonAction(),
    ).withDefault { DummyButtonAction() }

    fun executeDoublePress(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("double_press_eink_action", "open_menu")]?.execute(context)
    }

    fun executeSinglePress(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("single_press_eink_action", "clear")]?.execute(context)
    }

    fun executeDoublePressScreenOff(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("double_press_eink_action_screen_off", "dummy")]?.execute(context)
    }

    fun executeSinglePressScreenOff(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("single_press_eink_action_screen_off", "dummy")]?.execute(context)
    }
}