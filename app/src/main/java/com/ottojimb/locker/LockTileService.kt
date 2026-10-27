package com.ottojimb.locker

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: lock with the composite without using the power button. */
class LockTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = if (LockerService.instance != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    override fun onClick() {
        val service = LockerService.instance
        if (service != null) {
            service.lockNow()
        } else {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        }
    }
}
