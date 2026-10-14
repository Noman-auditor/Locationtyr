package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
fun ProfilesScreen(vm: MainViewModel, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val selected by vm.selectedId.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Profiles") {
            Icon(Icons.Filled.Add, "Add", tint = NoraColors.Mint, modifier = Modifier.clickableNoRipple(onAdd))
        }
        if (profiles.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("No profiles yet. Tap + to import a link or subscription.", color = NoraColors.TextDim)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(profiles, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                NoraCard(Modifier.fillMaxWidth(), onClick = { onOpen(p.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(CountryUtil.flag(p.country), fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(protoLabel(p), color = NoraColors.TextDim, fontSize = 12.sp)
                        }
                        if (p.id == selected) Badge("Selected")
                        Box {
                            Icon(
                                Icons.Filled.MoreVert, null, tint = NoraColors.TextDim,
                                modifier = Modifier.clickableNoRipple { menu = true },
                            )
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Select") }, onClick = { vm.select(p.id); menu = false })
                                DropdownMenuItem(text = { Text("Details") }, onClick = { onOpen(p.id); menu = false })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { vm.delete(p); menu = false })
                            }
                        }
                    }
                }
            }
        }
    }
}
