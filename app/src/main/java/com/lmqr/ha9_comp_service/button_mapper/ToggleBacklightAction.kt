package com.lmqr.ha9_comp_service.button_mapper

import SystemSettingsManager
import android.content.Context
import android.provider.Settings

class ToggleBacklightAction : ButtonAction {
    private var lastValue = 0
    override fun execute(context: Context) {
        val currentValue = SystemSettingsManager.getBrightnessFromSetting(context)
        SystemSettingsManager.setBrightnessSetting(context, if(currentValue <= 1) lastValue else 1)
        lastValue = currentValue
        val uri = Settings.System
            .getUriFor(Settings.System.SCREEN_BRIGHTNESS)
        context.applicationContext.contentResolver.notifyChange(uri, null)
    }
}