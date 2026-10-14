package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.data.Profile
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors
import kotlinx.coroutines.launch

private val dnsOptions = listOf(
    "1.1.1.1" to "Cloudflare (1.1.1.1)", "8.8.8.8" to "Google (8.8.8.8)",
    "9.9.9.9" to "Quad9 (9.9.9.9)", "" to "System default",
)

@Composable
fun SecurityScreen(vm: MainViewModel, selectedProfile: Profile?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val kill by vm.settings.killSwitchOn.collectAsState(false)
    val v6 by vm.settings.blockIpv6On.collectAsState(true)
    val dns by vm.settings.dnsServer.collectAsState("1.1.1.1")
    var dnsDialog by remember { mutableStateOf(false) }
    val dnsLabel = dnsOptions.firstOrNull { it.first == dns }?.second ?: dns
    val protections = listOf(kill, v6, dns.isNotEmpty()).count { it }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Security", onBack)
        InfoRow(
            "Selected profile security",
            selectedProfile?.let { "${it.security.uppercase()} • ${it.transport.uppercase()}" } ?: "None selected",
        )
        InfoRow("DNS", dnsLabel) { dnsDialog = true }
        ToggleRow("Kill Switch", "Block traffic if the tunnel drops", kill) { scope.launch { vm.settings.setKillSwitch(it) } }
        ToggleRow("Block IPv6", "Prevent IPv6 leaks outside the tunnel", v6) { scope.launch { vm.settings.setBlockIpv6(it) } }
        NoraCard(Modifier.fillMaxWidth()) {
            Text("Security Status", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "$protections of 3 protections enabled (kill switch, IPv6 block, custom DNS)",
                color = if (protections == 3) NoraColors.Mint else NoraColors.Orange, fontSize = 12.sp,
            )
            Text("Changes apply the next time you connect.", color = NoraColors.TextDim, fontSize = 11.sp)
        }
    }

    if (dnsDialog) {
        AlertDialog(
            onDismissRequest = { dnsDialog = false },
            title = { Text("DNS server") },
            text = {
                Column {
                    dnsOptions.forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickableNoRipple { scope.launch { vm.settings.setDns(value) }; dnsDialog = false }
                            .padding(vertical = 8.dp)) {
                            RadioButton(selected = dns == value, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dnsDialog = false }) { Text("Close") } },
        )
    }
}
