package com.nora.tunnel.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "1.0.0" } }
    var dialog by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("About", onBack)
        NoraLogo(72.dp)
        Text("NORA TUNNEL", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Secure. Private. Connected.", color = NoraColors.TextDim, fontSize = 13.sp)
        Text("Version $version", color = NoraColors.TextDim, fontSize = 12.sp)
        InfoRow("Developer", "Noman Khan")
        InfoRow("Package", ctx.packageName)
        InfoRow("Build", "2026.10.06")
        InfoRow("Privacy Policy", "") { dialog = "privacy" }
        InfoRow("Open Source Licenses", "") { dialog = "licenses" }
    }

    dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (d == "privacy") "Privacy Policy" else "Open Source Licenses") },
            text = {
                Text(
                    if (d == "privacy")
                        "Nora Tunnel stores your profiles and settings on this device only. The app has no analytics or ads. Network Lab speed tests contact speed.cloudflare.com, and subscription imports contact the URL you enter."
                    else
                        "AndroidX, Jetpack Compose, Room, DataStore, Navigation and Kotlin Coroutines (Apache License 2.0). JSch by mwiede (BSD). Xray-core (MPL-2.0), hev-socks5-tunnel (MIT) and dnstt (public domain) when bundled."
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )
    }
}
