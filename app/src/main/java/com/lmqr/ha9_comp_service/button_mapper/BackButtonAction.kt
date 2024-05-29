package com.lmqr.ha9_comp_service.button_mapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.content.Context

class BackButtonAction : ButtonAction {
    override fun execute(context: Context) {
        (context as? AccessibilityService)?.performGlobalAction(GLOBAL_ACTION_BACK)
    }

}