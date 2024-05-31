package com.lmqr.ha9_comp_service.quick_settings

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.preference.PreferenceManager
import com.lmqr.ha9_comp_service.R

class ClearScreenTile: TileService() {
    private fun updateTile() {
        val tile = qsTile
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Clear Screen"
        tile.icon = Icon.createWithResource(
            this,
            R.drawable.baseline_photo_filter_24
        )
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        PreferenceManager.getDefaultSharedPreferences(this).toggle("run_clear_screen")
    }
}