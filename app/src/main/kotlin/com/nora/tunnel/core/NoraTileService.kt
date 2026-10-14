package com.nora.tunnel.core

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nora.tunnel.MainActivity
import kotlinx.coroutines.*

class NoraTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        job = scope.launch { VpnController.state.collect { render(it) } }
    }

    override fun onStopListening() { job?.cancel() }

    override fun onClick() {
        if (VpnController.toggle(this)) return
        // VPN permission not granted yet: open the app so the user can approve it.
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }

    private fun render(s: VpnState) {
        val t = qsTile ?: return
        t.state = if (s == VpnState.CONNECTED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Nora Tunnel"
        if (Build.VERSION.SDK_INT >= 29) {
            t.subtitle = when (s) {
                VpnState.CONNECTED -> "Connected"; VpnState.CONNECTING -> "Connecting…"
                VpnState.ERROR -> "Error"; else -> "Off"
            }
        }
        t.updateTile()
    }
}
