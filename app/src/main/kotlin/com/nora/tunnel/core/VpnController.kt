package com.nora.tunnel.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Process-wide connection state shared by the service, UI, tile and widget. */
object VpnController {
    data class Live(
        val rx: Long = 0, val tx: Long = 0, val downBps: Long = 0, val upBps: Long = 0,
        val pingMs: Int? = null, val name: String? = null,
    )

    private val _state = MutableStateFlow(VpnState.DISCONNECTED)
    val state: StateFlow<VpnState> = _state
    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @Volatile var autoTried = false

    internal fun setConnecting() { _error.value = null; _state.value = VpnState.CONNECTING }
    internal fun setConnected(name: String) { _live.value = Live(name = name); _state.value = VpnState.CONNECTED }
    internal fun setError(msg: String) { _error.value = msg; _state.value = VpnState.ERROR }
    internal fun setDisconnected() { _live.value = Live(); _state.value = VpnState.DISCONNECTED }
    internal fun updateStats(rx: Long, tx: Long, down: Long, up: Long) {
        _live.value = _live.value.copy(rx = rx, tx = tx, downBps = down, upBps = up)
    }
    internal fun updatePing(ms: Int?) { _live.value = _live.value.copy(pingMs = ms) }

    fun start(ctx: Context) {
        AppLog.add("INFO", "Start requested")
        ContextCompat.startForegroundService(
            ctx, Intent(ctx, NoraVpnService::class.java).setAction(NoraVpnService.ACTION_CONNECT),
        )
    }

    fun stop(ctx: Context) {
        AppLog.add("INFO", "Stop requested")
        ContextCompat.startForegroundService(
            ctx, Intent(ctx, NoraVpnService::class.java).setAction(NoraVpnService.ACTION_DISCONNECT),
        )
    }

    /** Returns false when the VPN permission dialog must be shown first (needs an Activity). */
    fun toggle(ctx: Context): Boolean {
        val st = _state.value
        if (st == VpnState.CONNECTED || st == VpnState.CONNECTING) { stop(ctx); return true }
        if (VpnService.prepare(ctx) != null) return false
        start(ctx)
        return true
    }
}
