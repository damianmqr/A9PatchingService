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
        "home" to HomeButtonAction(),
        "recents" to RecentsButtonAction(),
        "open_menu" to ToggleMenuButtonAction(),
        "toggle_night_light" to ToggleNightLightAction(),
        "toggle_backlight" to ToggleBacklightAction(),
    ).withDefault { DummyButtonAction() }

    fun executeLongPress(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("long_press_eink_action", "dummy")]?.execute(context)
    }

    fun executeDoublePress(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("double_press_eink_action", "open_menu")]?.execute(context)
    }

    fun executeSinglePress(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("single_press_eink_action", "clear")]?.execute(context)
    }

    fun executeLongPressScreenOff(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("long_press_eink_action_screen_off", "dummy")]?.execute(context)
    }

    fun executeDoublePressScreenOff(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("double_press_eink_action_screen_off", "dummy")]?.execute(context)
    }

    fun executeSinglePressScreenOff(context: Context){
        buttonCommands[PreferenceManager.getDefaultSharedPreferences(context).getString("single_press_eink_action_screen_off", "dummy")]?.execute(context)
    }
}