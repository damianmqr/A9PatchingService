package com.lmqr.ha9_comp_service.quick_settings

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.R
import com.lmqr.ha9_comp_service.SettingsActivity

class LaunchSettingsTile : TileService() {

    private fun updateTile() {
        val tile = qsTile
        tile.state = Tile.STATE_INACTIVE
        tile.label = "E-Ink settings"
        tile.icon = Icon.createWithResource(
            this,
            R.drawable.baseline_settings_24
        )
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)

        PreferenceManager.getDefaultSharedPreferences(this).toggle("close_status_bar")
    }
}
