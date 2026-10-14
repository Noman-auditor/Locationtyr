package com.nora.tunnel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.ui.components.NoraCard
import com.nora.tunnel.ui.theme.NoraColors

@Composable
fun PlaceholderScreen(title: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Coming in the next build phase.", color = NoraColors.TextDim)
    }
}

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        "Server Location" to "servers", "Import Profile" to "import", "Security" to "security",
        "Logs" to "logs", "Statistics" to "stats", "Settings" to "settings", "About" to "about",
    )
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("More", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        items.forEach { (label, route) ->
            NoraCard(Modifier.fillMaxWidth(), onClick = { onNavigate(route) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = NoraColors.TextDim)
                }
            }
        }
    }
}
