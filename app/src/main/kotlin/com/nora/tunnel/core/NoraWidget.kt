package com.nora.tunnel.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nora.tunnel.MainActivity
import com.nora.tunnel.R
import com.nora.tunnel.ui.components.formatBytes

class NoraWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_TOGGLE && !VpnController.toggle(ctx)) {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.nora.tunnel.WIDGET_TOGGLE"

        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, NoraWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, m, ids)
        }

        private fun render(ctx: Context, m: AppWidgetManager, ids: IntArray) {
            val st = VpnController.state.value
            val live = VpnController.live.value
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            v.setTextViewText(R.id.widget_status, when (st) {
                VpnState.CONNECTED -> "Connected"; VpnState.CONNECTING -> "Connecting…"
                VpnState.ERROR -> "Error"; else -> "Disconnected"
            })
            v.setTextViewText(
                R.id.widget_stats,
                if (st == VpnState.CONNECTED) "${live.name ?: ""}  ↓ ${formatBytes(live.rx)}  ↑ ${formatBytes(live.tx)}"
                else "Tap power to connect",
            )
            val toggle = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, NoraWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_toggle, toggle)
            v.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            m.updateAppWidget(ids, v)
        }
    }
}
