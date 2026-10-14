package com.nora.tunnel.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.nora.tunnel.NoraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val on = (ctx.applicationContext as NoraApp).settings.startOnBootOn.first()
                if (on && VpnService.prepare(ctx) == null) VpnController.start(ctx)
            } finally { pending.finish() }
        }
    }
}
