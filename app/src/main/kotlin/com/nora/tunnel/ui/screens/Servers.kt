package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.core.CountryUtil
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors

@Composable
private fun Radio(selected: Boolean) = RadioButton(
    selected = selected, onClick = null,
    colors = RadioButtonDefaults.colors(selectedColor = NoraColors.Mint, unselectedColor = NoraColors.Border),
)

@Composable
fun ServersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val selected by vm.selectedId.collectAsState()
    val pings by vm.pings.collectAsState()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(profiles.size) { vm.pingAll() }

    val filtered = profiles.filter {
        query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Server Location", onBack) {
            Icon(Icons.Filled.Refresh, "Re-test", tint = NoraColors.Mint, modifier = Modifier.clickableNoRipple { vm.pingAll() })
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search country or server...") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                NoraCard(Modifier.fillMaxWidth(), onClick = { vm.select(-1L) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\uD83C\uDF10", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto Select", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Best Performance", color = NoraColors.TextDim, fontSize = 12.sp)
                        }
                        Radio(selected == -1L)
                    }
                }
            }
            items(filtered, key = { it.id }) { p ->
                NoraCard(Modifier.fillMaxWidth(), onClick = { vm.select(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(CountryUtil.flag(p.country), fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(pings[p.id]?.let { "$it ms" } ?: "-- ms", color = NoraColors.TextDim, fontSize = 12.sp)
                        }
                        Radio(selected == p.id)
                    }
                }
            }
        }
    }
}
