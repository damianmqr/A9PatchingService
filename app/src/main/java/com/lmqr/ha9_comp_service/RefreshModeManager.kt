package com.lmqr.ha9_comp_service

import android.content.SharedPreferences

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
    private val rootCommandRunner: RootCommandRunner,
) {
    var currentMode = RefreshMode.NONE
        private set(v) { field = v; applyMode() }
    private var currentClassifier = ""
    fun onAppChange(packageName: String) = packageName.toClassifier().run{
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
        RefreshMode.CLEAR -> rootCommandRunner.runAsRoot(arrayOf("echo 515 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode"))
        RefreshMode.BALANCED -> rootCommandRunner.runAsRoot(arrayOf("echo 513 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode"))
        RefreshMode.SMOOTH -> rootCommandRunner.runAsRoot(arrayOf("echo 518 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode"))
        RefreshMode.SPEED -> rootCommandRunner.runAsRoot(arrayOf("echo 521 > /sys/devices/platform/soc/soc\\:qcom,dsi-display-primary/epd_display_mode"))
        RefreshMode.NONE -> {}
    }

    companion object{
        const val systemClassifier = "android"
    }
}

private fun String.toClassifier(): String {
    if(startsWith("com.android"))
        return RefreshModeManager.systemClassifier
    return this
}

private fun String.toSharedPreferencesKey(isPerAppEnabled: Boolean): String {
    if(isPerAppEnabled) {
        if (startsWith("package:"))
            return this
        return "package:$this"
    }
    return "package:None"
}