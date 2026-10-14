package com.nora.tunnel.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.core.Diagnostics
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors
import kotlinx.coroutines.launch

@Composable
fun LabScreen() {
    val tabs = listOf("Ping", "Traceroute", "DNS", "Speed")
    var tab by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf("8.8.8.8") }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run() {
        if (running) return
        if (tab < 3 && !Diagnostics.validHost(host.trim())) { lines = listOf("Enter a valid host or IP"); return }
        running = true
        lines = emptyList()
        val h = host.trim()
        scope.launch {
            try {
                when (tab) {
                    0 -> {
                        val r = Diagnostics.ping(h)
                        lines = if (r.times.isEmpty()) listOf("Packets: 0/${r.sent}", "No reply (host down or ICMP blocked)")
                        else listOf(
                            "Packets: ${r.received}/${r.sent}",
                            "Min: %.0f ms".format(r.times.min()),
                            "Avg: %.0f ms".format(r.times.average()),
                            "Max: %.0f ms".format(r.times.max()),
                        )
                    }
                    1 -> Diagnostics.traceroute(h) { lines = lines + it }
                    2 -> {
                        val (ms, addrs) = Diagnostics.dns(h)
                        lines = listOf("Resolved in $ms ms") + addrs
                    }
                    else -> {
                        lines = listOf("Testing download...")
                        val d = Diagnostics.downloadMbps()
                        lines = listOf("Download: %.1f Mbps".format(d), "Testing upload...")
                        val u = Diagnostics.uploadMbps()
                        lines = listOf("Download: %.1f Mbps".format(d), "Upload: %.1f Mbps".format(u))
                    }
                }
            } catch (e: Exception) {
                lines = lines + "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                running = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Network Lab")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, t ->
                FilterChip(
                    selected = tab == i, onClick = { tab = i; lines = emptyList() }, label = { Text(t) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NoraColors.Mint, selectedLabelColor = NoraColors.OnMint,
                    ),
                )
            }
        }
        if (tab < 3) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host, onValueChange = { host = it }, singleLine = true,
                    label = { Text("Host or IP address") }, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { run() }, enabled = !running,
                    colors = ButtonDefaults.buttonColors(containerColor = NoraColors.Mint, contentColor = NoraColors.OnMint),
                ) { Text(tabs[tab].let { if (it == "Traceroute") "Trace" else if (it == "DNS") "Lookup" else it }) }
            }
        } else {
            PrimaryButton(if (running) "Running..." else "Start Speed Test") { run() }
            Text("Uses speed.cloudflare.com. Results reflect your current connection.", color = NoraColors.TextDim, fontSize = 11.sp)
        }
        NoraCard(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (lines.isEmpty()) Text(if (running) "Running..." else "Results appear here", color = NoraColors.TextDim)
                lines.forEach { Text(it, color = Color.White, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
            }
        }
    }
}
