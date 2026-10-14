package com.nora.tunnel.core

import com.nora.tunnel.data.Profile
import org.json.JSONArray
import org.json.JSONObject

object XrayConfig {
    const val SOCKS_PORT = 10808

    private val lanCidrs = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8",
        "169.254.0.0/16", "fc00::/7", "fe80::/10",
    )

    private fun JSONObject.flag(k: String) = optString(k).let { it == "1" || it.equals("true", true) }

    /** [serverIp] is the already-resolved address, so no DNS is needed before the tunnel is up. */
    fun build(p: Profile, serverIp: String, dns: String, routingMode: String): String {
        val extra = JSONObject(p.extra.ifBlank { "{}" })
        val rules = JSONArray()
        if (routingMode == "bypass_lan") {
            rules.put(JSONObject().put("type", "field").put("ip", JSONArray(lanCidrs)).put("outboundTag", "direct"))
        }
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(
                JSONObject().put("tag", "socks").put("listen", "127.0.0.1").put("port", SOCKS_PORT)
                    .put("protocol", "socks")
                    .put("settings", JSONObject().put("udp", true).put("auth", "noauth").put("ip", "127.0.0.1"))
                    .put("sniffing", JSONObject().put("enabled", true)
                        .put("destOverride", JSONArray().put("http").put("tls")))
            ))
            .put("outbounds", JSONArray()
                .put(outbound(p, serverIp, extra))
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole")))
            .put("dns", JSONObject().put("servers", JSONArray().put(dns)))
            .put("routing", JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", rules))
            .toString()
    }

    private fun outbound(p: Profile, ip: String, x: JSONObject): JSONObject {
        if (p.protocol == "ss" && x.optString("unsupportedPlugin").isNotBlank()) {
            throw IllegalArgumentException("Shadowsocks plugin '${x.optString("unsupportedPlugin")}' is not supported (only v2ray-plugin)")
        }
        val proto = when (p.protocol) {
            "ss" -> "shadowsocks"
            "vless", "vmess", "trojan", "socks", "http" -> p.protocol
            else -> throw IllegalArgumentException("Protocol '${p.protocol}' is not supported by the Xray engine")
        }
        val o = JSONObject().put("tag", "proxy").put("protocol", proto)
        val settings = when (p.protocol) {
            "vless" -> JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", ip).put("port", p.port).put("users", JSONArray().put(
                    JSONObject().put("id", p.secret).put("encryption", x.optString("encryption").ifBlank { "none" })
                        .apply { x.optString("flow").takeIf { it.isNotBlank() }?.let { put("flow", it) } }
                ))))
            "vmess" -> JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", ip).put("port", p.port).put("users", JSONArray().put(
                    JSONObject().put("id", p.secret).put("alterId", x.optString("aid").toIntOrNull() ?: 0)
                        .put("security", x.optString("scy").ifBlank { "auto" })
                ))))
            "trojan" -> JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", ip).put("port", p.port).put("password", p.secret)))
            "ss" -> JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", ip).put("port", p.port).put("password", p.secret)
                    .put("method", ssMethod(x.optString("method", "aes-256-gcm")))))
            else -> { // socks, http
                val user = x.optString("user")
                JSONObject().put("servers", JSONArray().put(
                    JSONObject().put("address", ip).put("port", p.port).apply {
                        if (user.isNotBlank()) put("users", JSONArray().put(JSONObject().put("user", user).put("pass", p.secret)))
                    }))
            }
        }
        o.put("settings", settings)
        val needStream = p.protocol in setOf("vless", "vmess", "trojan") ||
            p.security != "none" || p.transport.ifBlank { "tcp" } != "tcp"
        if (needStream) o.put("streamSettings", stream(p, x))
        return o
    }

    private fun ssMethod(m: String) = when (m.lowercase()) {
        "chacha20-poly1305" -> "chacha20-ietf-poly1305"
        "xchacha20-poly1305" -> "xchacha20-ietf-poly1305"
        else -> m
    }

    private fun stream(p: Profile, x: JSONObject): JSONObject {
        val net = p.transport.ifBlank { "tcp" }
        val sec = p.security.ifBlank { "none" }
        val s = JSONObject().put("network", net).put("security", sec)
        val host = x.optString("host")
        val path = x.optString("path").ifBlank { "/" }
        val sni = x.optString("sni").ifBlank { host.ifBlank { p.address } }
        // The server is dialled by IP, so always send an explicit Host/authority.
        val hostHdr = host.ifBlank { if (sec == "tls" || sec == "reality") sni else p.address }
        when (sec) {
            "tls" -> s.put("tlsSettings", JSONObject().put("serverName", sni)
                .put("allowInsecure", x.flag("allowInsecure") || x.flag("insecure"))
                .apply {
                    x.optString("fp").takeIf { it.isNotBlank() }?.let { put("fingerprint", it) }
                    x.optString("alpn").takeIf { it.isNotBlank() }?.let { put("alpn", JSONArray(it.split(","))) }
                })
            "reality" -> s.put("realitySettings", JSONObject().put("serverName", sni)
                .put("fingerprint", x.optString("fp").ifBlank { "chrome" })
                .put("publicKey", x.optString("pbk")).put("shortId", x.optString("sid"))
                .put("spiderX", x.optString("spx")))
        }
        when (net) {
            "tcp" -> if (x.optString("headerType") == "http") {
                s.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http")
                    .put("request", JSONObject()
                        .put("path", JSONArray(path.split(",")))
                        .put("headers", JSONObject().put("Host", JSONArray(hostHdr.split(",")))))))
            }
            "ws" -> s.put("wsSettings", JSONObject().put("path", path)
                .put("headers", JSONObject().put("Host", hostHdr)))
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", x.optString("serviceName"))
                .apply { x.optString("authority").takeIf { it.isNotBlank() }?.let { put("authority", it) } })
            "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", path).put("host", hostHdr))
            "xhttp" -> s.put("xhttpSettings", JSONObject().put("path", path).put("host", hostHdr))
            "kcp" -> s.put("kcpSettings", JSONObject()
                .put("header", JSONObject().put("type", x.optString("headerType").ifBlank { "none" }))
                .apply { x.optString("seed").takeIf { it.isNotBlank() }?.let { put("seed", it) } })
            "h2", "http" -> s.put("httpSettings", JSONObject().put("path", path)
                .put("host", JSONArray().put(hostHdr)))
        }
        return s
    }
}
