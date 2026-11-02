package com.example.shakelight

import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ShakeTileService : TileService() {

    // Se lance quand tu affiches le menu déroulant
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    // Se lance quand tu appuies sur la tuile
    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("ShakeLightPrefs", Context.MODE_PRIVATE)
        val isPaused = prefs.getBoolean("is_paused", false)

        // Inverse l'état (si en pause -> actif, si actif -> en pause)
        prefs.edit().putBoolean("is_paused", !isPaused).apply()
        updateTileState()
    }

    private fun updateTileState() {
        val prefs = getSharedPreferences("ShakeLightPrefs", Context.MODE_PRIVATE)
        val isPaused = prefs.getBoolean("is_paused", false)
        val tile = qsTile ?: return

        if (isPaused) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Shake (Pause)"
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Shake (Actif)"
        }
        tile.updateTile()
    }
}