package com.nora.tunnel.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import com.nora.tunnel.core.Diag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nora.tunnel.NoraApp
import com.nora.tunnel.core.AppLog
import com.nora.tunnel.core.VpnController
import com.nora.tunnel.core.VpnState
import com.nora.tunnel.ui.components.NoraCard
import com.nora.tunnel.ui.components.formatBytes
import com.nora.tunnel.ui.theme.NoraColors
import kotlinx.coroutines.flow.first

@Composable
fun HomeScreen(profileName: String?, recent: List<AppLog.Entry>, onServers: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val state by VpnController.state.collectAsState()
    val live by VpnController.live.collectAsState()
    val error by VpnController.error.collectAsState()

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        AppLog.add("INFO", "VPN permission result: ${it.resultCode}")
        if (it.resultCode == Activity.RESULT_OK) VpnController.start(ctx)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (!VpnController.autoTried) {
            VpnController.autoTried = true
            val auto = (ctx.applicationContext as NoraApp).settings.autoConnectOn.first()
            if (auto && VpnController.state.value == VpnState.DISCONNECTED && VpnService.prepare(ctx) == null) {
                VpnController.start(ctx)
            }
        }
    }

    fun onPower() {
        AppLog.add("INFO", "Power button tapped (state=$state)")
        if (state == VpnState.CONNECTED || state == VpnState.CONNECTING) { VpnController.stop(ctx); return }
        val i = VpnService.prepare(ctx)
        if (i != null) vpnPermission.launch(i) else VpnController.start(ctx)
    }

    val accent = when (state) {
        VpnState.CONNECTED -> NoraColors.Mint
        VpnState.ERROR -> NoraColors.Red
        VpnState.CONNECTING -> NoraColors.Orange
        else -> NoraColors.Blue
    }
    val title = when (state) {
        VpnState.CONNECTED -> "Connected"; VpnState.CONNECTING -> "Connecting…"
        VpnState.ERROR -> "Connection failed"; else -> "Disconnected"
    }
    val hint = when (state) {
        VpnState.CONNECTED, VpnState.CONNECTING -> "Tap to disconnect"
        VpnState.ERROR -> "Tap to retry"; else -> "Tap to connect"
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = NoraColors.Mint)
            Spacer(Modifier.width(8.dp))
            Text("NORA TUNNEL", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Settings, null, tint = NoraColors.Mint, modifier = Modifier.clickable { onSettings() })
        }

        val crash by Diag.lastReport.collectAsState()
        crash?.let { report ->
            NoraCard(Modifier.fillMaxWidth()) {
                Text("The app stopped last time", color = NoraColors.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                SelectionContainer { Text(report, color = Color.White, fontSize = 11.sp) }
                Spacer(Modifier.height(6.dp))
                Text("Dismiss", color = NoraColors.Mint, fontSize = 12.sp, modifier = Modifier.clickable { Diag.dismiss() })
            }
        }

        NoraCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = accent, fontSize = 13.sp)
                Text(hint, color = NoraColors.TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.size(96.dp).clip(CircleShape)
                        .border(BorderStroke(3.dp, accent), CircleShape)
                        .clickable { onPower() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
                if (state == VpnState.ERROR && error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error ?: "", color = NoraColors.Red, fontSize = 11.sp)
                }
            }
        }

        NoraCard(Modifier.fillMaxWidth(), onClick = onServers) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(NoraColors.Blue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text("\uD83C\uDF10") }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(live.name ?: profileName ?: "Auto Select", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (live.name != null || profileName != null) "Selected profile" else "Best location",
                        color = NoraColors.TextDim, fontSize = 12.sp,
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = NoraColors.TextDim)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(formatBytes(live.rx), "Download", Modifier.weight(1f))
            StatTile(formatBytes(live.tx), "Upload", Modifier.weight(1f))
            StatTile(live.pingMs?.let { "$it ms" } ?: "--", "Ping", Modifier.weight(1f))
        }
        if (state == VpnState.CONNECTED) {
            Text(
                "↓ ${formatBytes(live.downBps)}/s   ↑ ${formatBytes(live.upBps)}/s",
                color = NoraColors.TextDim, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        NoraCard(Modifier.fillMaxWidth()) {
            Text("Recent Activity", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (recent.isEmpty()) {
                Text("No recent logs", color = NoraColors.TextDim, fontSize = 12.sp)
            } else {
                recent.take(3).forEach {
                    Text("${it.type}: ${it.message}", color = NoraColors.TextDim, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Text("Build 1.0.6 (r6)", color = NoraColors.TextDim, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    NoraCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(label, color = NoraColors.TextDim, fontSize = 11.sp)
        }
    }
}
