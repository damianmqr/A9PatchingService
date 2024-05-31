package com.lmqr.ha9_comp_service.quick_settings

import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.R

class NightModeTile : TileService() {

    private lateinit var sharedPreferences: SharedPreferences
    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "night_mode", "disable_nightmode" -> updateTile()
            }
        }

    private fun updateTile() {
        val disableNightMode = sharedPreferences.getBoolean("disable_nightmode", false)
        val nightModeEnabled = sharedPreferences.getBoolean(nightModeKey, false)
        val tile = qsTile
        tile.state = when {
            disableNightMode -> Tile.STATE_UNAVAILABLE
            nightModeEnabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE

        }
        tile.label = if (nightModeEnabled) "Night Mode On" else "Night Mode Off"
        tile.icon = Icon.createWithResource(
            this,
            if (nightModeEnabled) R.drawable.baseline_moon_24_white else R.drawable.baseline_brightness_high_24
        )
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        sharedPreferences.toggle(nightModeKey)
    }

    override fun onStopListening() {
        super.onStopListening()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    companion object {
        const val nightModeKey = "night_mode"
    }
}