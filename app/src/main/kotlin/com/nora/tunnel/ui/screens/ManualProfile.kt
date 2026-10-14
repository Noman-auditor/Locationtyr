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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.core.CountryUtil
import com.nora.tunnel.data.Profile
import com.nora.tunnel.ui.components.*
import com.nora.tunnel.ui.theme.NoraColors
import org.json.JSONObject

private val protocols = listOf(
    "ssh" to "SSH", "vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan",
    "ss" to "Shadowsocks", "socks" to "SOCKS5", "http" to "HTTP Proxy",
)
private val sshModes = listOf(
    "direct" to "Direct", "payload" to "Payload", "proxy" to "HTTP Proxy",
    "tls" to "SSL/TLS", "ws" to "WebSocket (WS/WSS)", "dnstt" to "DNSTT (DNS tunnel)",
)
private val transports = listOf("tcp", "ws", "grpc", "httpupgrade", "kcp", "h2")
private val ssMethods = listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305")

@Composable
private fun Chips(items: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (k, label) ->
            FilterChip(
                selected = selected == k, onClick = { onPick(k) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NoraColors.Mint, selectedLabelColor = NoraColors.OnMint,
                ),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false, lines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = lines == 1, minLines = lines, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )
}

@Composable
private fun Check(label: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(v, onChange)
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun ManualProfileScreen(vm: MainViewModel, onBack: () -> Unit) {
    var proto by remember { mutableStateOf("ssh") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var sshKey by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("direct") }
    var payload by remember { mutableStateOf("") }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("8080") }
    var sni by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(false) }
    var wss by remember { mutableStateOf(true) }
    var payloadTls by remember { mutableStateOf(false) }
    var wsPath by remember { mutableStateOf("/") }
    var wsHost by remember { mutableStateOf("") }
    var dnsMode by remember { mutableStateOf("udp") }
    var dnsServer by remember { mutableStateOf("") }
    var dnsDomain by remember { mutableStateOf("") }
    var dnsPubkey by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("tcp") }
    var tls by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("/") }
    var host by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("aes-256-gcm") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val isDnstt = proto == "ssh" && mode == "dnstt"
        val addr = if (isDnstt) dnsDomain.trim() else address.trim()
        if (addr.isBlank()) { error = if (isDnstt) "Enter the DNSTT domain" else "Enter the server address"; return }
        if (isDnstt && (dnsServer.isBlank() || dnsPubkey.isBlank())) { error = "DNSTT needs a resolver and the server public key"; return }
        val prt = port.trim().toIntOrNull() ?: when (proto) { "ssh" -> 22; "socks" -> 1080; "http" -> 8080; else -> 443 }
        if (prt !in 1..65535) { error = "Invalid port"; return }
        if (proto in listOf("vless", "vmess", "trojan", "ss") && secret.isBlank()) { error = "Enter the UUID / password"; return }
        if (proto == "ssh" && user.isBlank()) { error = "Enter the SSH username"; return }
        if (proto == "ssh" && mode == "proxy" && proxyHost.isBlank()) { error = "Enter the proxy host"; return }

        val x = JSONObject()
        var tr = "tcp"
        var sec = "none"
        when (proto) {
            "ssh" -> {
                x.put("user", user.trim()).put("mode", mode)
                if (sshKey.isNotBlank()) x.put("key", sshKey)
                if (mode == "payload" || mode == "proxy" || mode == "ws") x.put("payload", payload)
                if (mode == "proxy") x.put("proxyHost", proxyHost.trim()).put("proxyPort", proxyPort.trim())
                if (mode == "tls" || mode == "payload" || mode == "ws") {
                    x.put("sni", sni.trim()).put("insecure", if (insecure) "1" else "0")
                }
                if (mode == "payload") x.put("tls", if (payloadTls) "1" else "0")
                if (mode == "ws") { x.put("tls", if (wss) "1" else "0").put("wsPath", wsPath).put("wsHost", wsHost.trim()) }
                if (isDnstt) {
                    x.put("dnsMode", dnsMode).put("dnsServer", dnsServer.trim())
                        .put("dnsDomain", dnsDomain.trim()).put("dnsPubkey", dnsPubkey.trim())
                }
            }
            "vless", "vmess", "trojan", "ss" -> {
                tr = transport
                sec = if (tls || proto == "trojan") "tls" else "none"
                if (path.isNotBlank()) x.put("path", path)
                if (host.isNotBlank()) x.put("host", host.trim())
                if (sni.isNotBlank()) x.put("sni", sni.trim())
                if (insecure) x.put("allowInsecure", "1")
                if (transport == "grpc") x.put("serviceName", path.trim('/'))
                if (proto == "ss") x.put("method", method)
            }
            else -> {
                if (user.isNotBlank()) x.put("user", user.trim())
                if (tls) sec = "tls"
            }
        }
        val label = name.trim().ifBlank { "${protocols.first { it.first == proto }.second} $addr" }
        vm.addProfile(
            Profile(
                name = label, protocol = proto, address = addr, port = prt, secret = secret,
                transport = tr, security = sec, extra = x.toString(), country = CountryUtil.fromName(label),
            ),
        ) { onBack() }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Add Profile", onBack)
        Chips(protocols, proto) { proto = it; error = null }
        Field("Name (optional)", name, { name = it })
        if (!(proto == "ssh" && mode == "dnstt")) {
            Field("Server address", address, { address = it })
            Field("Port", port, { port = it }, number = true)
        }

        when (proto) {
            "ssh" -> {
                Text("Connection mode", color = NoraColors.TextDim, fontSize = 12.sp)
                Chips(sshModes, mode) { mode = it }
                Field("SSH username", user, { user = it })
                Field("SSH password (or key passphrase)", secret, { secret = it })
                Field("Private key (optional, PEM)", sshKey, { sshKey = it }, lines = 3)
                if (mode == "payload" || mode == "proxy" || mode == "ws") {
                    Field(
                        if (mode == "ws") "Custom payload (blank = standard WebSocket upgrade)" else "Payload",
                        payload, { payload = it }, lines = 3,
                    )
                    Text("Tags: [crlf] [cr] [lf] [host] [port] [host_port] [protocol] [split]", color = NoraColors.TextDim, fontSize = 11.sp)
                    if (mode == "payload") Check("Use TLS before the payload (SSL + payload)", payloadTls) { payloadTls = it }
                }
                if (mode == "proxy") {
                    Field("HTTP proxy host", proxyHost, { proxyHost = it })
                    Field("HTTP proxy port", proxyPort, { proxyPort = it }, number = true)
                }
                if (mode == "tls" || mode == "payload" || mode == "ws") {
                    Field("SNI / bug host (optional)", sni, { sni = it })
                    Check("Allow insecure TLS (needed when SNI differs from the certificate)", insecure) { insecure = it }
                }
                if (mode == "ws") {
                    Check("Use TLS (WSS)", wss) { wss = it }
                    Field("WebSocket path", wsPath, { wsPath = it })
                    Field("WebSocket Host header (optional)", wsHost, { wsHost = it })
                }
                if (mode == "dnstt") {
                    Text("Resolver type", color = NoraColors.TextDim, fontSize = 12.sp)
                    Chips(listOf("udp" to "UDP", "doh" to "DoH", "dot" to "DoT"), dnsMode) { dnsMode = it }
                    Field(
                        when (dnsMode) { "doh" -> "Resolver URL (use an IP, e.g. https://1.1.1.1/dns-query)"; "dot" -> "Resolver host:port (IP, e.g. 1.1.1.1:853)"; else -> "Resolver IP:port (e.g. 8.8.8.8:53)" },
                        dnsServer, { dnsServer = it },
                    )
                    Field("Tunnel domain (NS record)", dnsDomain, { dnsDomain = it })
                    Field("Server public key (hex)", dnsPubkey, { dnsPubkey = it })
                }
            }
            "vless", "vmess", "trojan", "ss" -> {
                Field(if (proto == "vless" || proto == "vmess") "UUID" else "Password", secret, { secret = it })
                if (proto == "ss") {
                    Chips(ssMethods.map { it to it }, method) { method = it }
                }
                Text("Transport", color = NoraColors.TextDim, fontSize = 12.sp)
                Chips(transports.map { it to it.uppercase() }, transport) { transport = it }
                if (proto != "trojan") Check("TLS / SSL (with WS this is WSS)", tls) { tls = it }
                Field(if (transport == "grpc") "gRPC service name" else "Path", path, { path = it })
                Field("Host header (optional)", host, { host = it })
                Field("SNI (optional)", sni, { sni = it })
                Check("Allow insecure TLS", insecure) { insecure = it }
            }
            else -> {
                Field("Username (optional)", user, { user = it })
                Field("Password (optional)", secret, { secret = it })
                if (proto == "http") Check("HTTPS proxy (TLS)", tls) { tls = it }
            }
        }

        error?.let { Text(it, color = NoraColors.Red, fontSize = 13.sp) }
        PrimaryButton("Save profile") { save() }
        Spacer(Modifier.height(16.dp))
    }
}
