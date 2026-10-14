package com.nora.tunnel.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.core.AppLog
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by AppLog.entries.collectAsState()
    val filters = listOf("All", "Connect", "Disconnect", "Error")
    var f by remember { mutableIntStateOf(0) }
    val shown = if (f == 0) all else all.filter { it.type.equals(filters[f], true) }
    val fmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Logs", onBack) {
            Icon(
                Icons.Filled.Share, "Export", tint = NoraColors.Mint,
                modifier = Modifier.clickableNoRipple {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, AppLog.export())
                    }
                    ctx.startActivity(Intent.createChooser(i, "Export logs"))
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, t ->
                FilterChip(
                    selected = f == i, onClick = { f = i }, label = { Text(t) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NoraColors.Mint, selectedLabelColor = NoraColors.OnMint,
                    ),
                )
            }
        }
        if (shown.isEmpty()) Text("No log entries", color = NoraColors.TextDim)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown) { e ->
                val c = when (e.type) {
                    "CONNECT" -> NoraColors.Mint; "DISCONNECT" -> NoraColors.Orange
                    "ERROR" -> NoraColors.Red; else -> NoraColors.Blue
                }
                NoraCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(e.type.lowercase().replaceFirstChar { it.uppercase() }, c)
                        Spacer(Modifier.width(10.dp))
                        Text(e.message, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(fmt.format(Date(e.time)), color = NoraColors.TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
