package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.core.AppLog
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val auto by vm.settings.autoConnectOn.collectAsState(false)
    val boot by vm.settings.startOnBootOn.collectAsState(false)
    val notif by vm.settings.notificationsOn.collectAsState(true)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Settings", onBack)
        Text("General", color = NoraColors.TextDim, fontSize = 12.sp)
        InfoRow("App Theme", "Dark")
        InfoRow("Language", "English")
        ToggleRow("Auto Connect", "Connect when the app opens", auto) { scope.launch { vm.settings.setAutoConnect(it) } }
        ToggleRow("Start on Boot", null, boot) { scope.launch { vm.settings.setStartOnBoot(it) } }
        Text("Advanced", color = NoraColors.TextDim, fontSize = 12.sp)
        ToggleRow("Notifications", null, notif) { scope.launch { vm.settings.setNotifications(it) } }
        InfoRow("Clear logs", "") { AppLog.clear() }
        Text("Android always shows the VPN status notification while connected; the Notifications switch does not hide it.", color = NoraColors.TextDim, fontSize = 11.sp)
    }
}
