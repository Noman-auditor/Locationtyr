package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors

@Composable
fun ProfileDetailsScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, onConnect: () -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val p = profiles.firstOrNull { it.id == id }
    val clipboard = LocalClipboardManager.current
    var reveal by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Profile Details", onBack)
        if (p == null) {
            Text("Profile not found.", color = NoraColors.TextDim)
            return@Column
        }
        NoraCard(Modifier.fillMaxWidth()) {
            Text(p.name, color = Color.White, fontSize = 16.sp)
            Text(protoLabel(p), color = NoraColors.TextDim, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        NoraCard(Modifier.fillMaxWidth()) {
            DetailRow("Protocol", p.protocol.uppercase())
            DetailRow("Address", p.address)
            DetailRow("Port", p.port.toString())
            if (p.protocol == "ssh") DetailRow("Mode", sshModeLabel(sshMode(p)))
            DetailRow(
                "UUID / Password",
                if (reveal) p.secret else "•".repeat(minOf(p.secret.length, 16)),
                onClick = { reveal = !reveal },
            )
            DetailRow("Transport", p.transport.uppercase())
            DetailRow("Security", p.security.uppercase())
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Connect") { vm.select(p.id); onConnect() }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(p.rawLink)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = p.rawLink.isNotBlank(),
        ) { Text("Copy Link") }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).let { if (onClick != null) it.clickableNoRipple(onClick) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NoraColors.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}
