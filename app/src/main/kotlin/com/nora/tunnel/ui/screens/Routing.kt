package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors

private val modes = listOf(
    Triple("global", "Default Route", "All traffic through tunnel"),
    Triple("bypass_lan", "Bypass LAN", "Exclude local network"),
    Triple("split", "Split Tunneling", "Select apps or domains"),
    Triple("custom", "Custom Rules", "Advanced routing rules"),
)

@Composable
fun RoutingScreen(vm: MainViewModel) {
    val mode by vm.routingMode.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Routing")
        modes.forEach { (key, title, sub) ->
            NoraCard(Modifier.fillMaxWidth(), onClick = { vm.setRouting(key) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(sub, color = NoraColors.TextDim, fontSize = 12.sp)
                    }
                    RadioButton(
                        selected = mode == key, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = NoraColors.Mint, unselectedColor = NoraColors.Border),
                    )
                }
            }
        }
        Text(
            "Default Route and Bypass LAN apply on the next connection. Split Tunneling and Custom Rules are not available yet (treated as Default Route).",
            color = NoraColors.TextDim, fontSize = 12.sp,
        )
    }
}
