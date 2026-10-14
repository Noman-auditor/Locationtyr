package com.nora.tunnel.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors
import org.json.JSONObject
import java.time.LocalDate

@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val json by vm.settings.trafficJson.collectAsState("{}")
    val tabs = listOf("Today", "7 Days", "30 Days")
    var tab by remember { mutableIntStateOf(0) }
    val days = if (tab == 2) 30 else 7
    val data = remember(json, days) {
        val o = JSONObject(json)
        val today = LocalDate.now()
        (days - 1 downTo 0).map { d ->
            val a = o.optJSONArray(today.minusDays(d.toLong()).toString())
            (a?.optLong(0) ?: 0L) to (a?.optLong(1) ?: 0L)
        }
    }
    val rx = if (tab == 0) data.last().first else data.sumOf { it.first }
    val tx = if (tab == 0) data.last().second else data.sumOf { it.second }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Statistics", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, t ->
                FilterChip(
                    selected = tab == i, onClick = { tab = i }, label = { Text(t) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NoraColors.Mint, selectedLabelColor = NoraColors.OnMint,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NoraCard(Modifier.weight(1f)) {
                Text(formatBytes(rx), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Download", color = NoraColors.Mint, fontSize = 12.sp)
            }
            NoraCard(Modifier.weight(1f)) {
                Text(formatBytes(tx), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Upload", color = NoraColors.Blue, fontSize = 12.sp)
            }
        }
        NoraCard(Modifier.fillMaxWidth()) {
            Text(if (tab == 2) "Last 30 days" else "Last 7 days", color = NoraColors.TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            TrafficChart(data.map { it.first }, data.map { it.second })
            if (rx + tx == 0L && data.all { it.first + it.second == 0L }) {
                Text("No traffic recorded yet. Data appears after you use the VPN.", color = NoraColors.TextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TrafficChart(down: List<Long>, up: List<Long>) {
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val n = down.size
        if (n < 2) return@Canvas
        val maxV = maxOf(1L, down.max(), up.max()).toFloat()
        fun x(i: Int) = size.width * i / (n - 1)
        fun y(v: Long) = size.height - (v / maxV) * size.height * 0.9f

        val line = Path().apply { down.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        val area = Path().apply {
            addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawLine(NoraColors.Border, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        drawPath(area, Brush.verticalGradient(listOf(NoraColors.Mint.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, NoraColors.Mint, style = Stroke(width = 4f))
        val upLine = Path().apply { up.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
        drawPath(upLine, NoraColors.Blue, style = Stroke(width = 3f))
    }
}
