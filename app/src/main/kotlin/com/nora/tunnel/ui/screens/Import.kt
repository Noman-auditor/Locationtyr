package com.nora.tunnel.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors

@Composable
fun ImportScreen(vm: MainViewModel, onManual: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var message by remember { mutableStateOf<String?>(null) }
    var showLink by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) { "" }
            vm.importText(text) { message = it }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Import Profile", onBack)
        ImportRow(Icons.Filled.Description, NoraColors.Blue, "Import from File", "Links, subscription or .ovpn file") {
            picker.launch("*/*")
        }
        ImportRow(Icons.Filled.Add, NoraColors.Orange, "Add Manually", "SSH, Payload, DNSTT, VLESS, VMess, Trojan, SS, SOCKS, HTTP") { onManual() }
        ImportRow(Icons.Filled.QrCodeScanner, NoraColors.Purple, "Import from QR Code", "Camera scanner arrives in a later phase") {
            message = "QR scanning is not built yet"
        }
        ImportRow(Icons.Filled.Link, NoraColors.Blue, "Import from Link", "URI or subscription URL") { showLink = true }
        ImportRow(Icons.Filled.ContentPaste, NoraColors.Mint, "Scan Config", "Auto-detect from clipboard") {
            val t = clipboard.getText()?.text.orEmpty()
            if (t.isBlank()) message = "Clipboard is empty" else vm.importText(t) { message = it }
        }
        message?.let { Text(it, color = NoraColors.Mint, fontSize = 13.sp) }
    }

    if (showLink) {
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text("Import from Link") },
            text = {
                OutlinedTextField(
                    value = linkText, onValueChange = { linkText = it },
                    placeholder = { Text("vless://, vmess://, trojan://, ss://, ssh://, socks5:// or https://…") },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLink = false
                    vm.importText(linkText) { message = it }
                    linkText = ""
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ImportRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    NoraCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(sub, color = NoraColors.TextDim, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = NoraColors.TextDim)
        }
    }
}
