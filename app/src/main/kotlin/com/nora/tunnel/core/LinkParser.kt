package com.nora.tunnel.core

import android.net.Uri
import android.util.Base64
import com.nora.tunnel.data.Profile
import org.json.JSONObject

object LinkParser {

    private val URI_KEYS = listOf(
        "path", "host", "sni", "flow", "pbk", "sid", "fp", "alpn", "serviceName", "headerType",
        "mode", "authority", "seed", "spx", "allowInsecure", "insecure", "encryption", "quicSecurity", "key",
    )
    private val SSH_KEYS = listOf(
        "mode", "payload", "sni", "proxyHost", "proxyPort", "tls", "insecure", "wsPath", "wsHost",
        "dnsMode", "dnsServer", "dnsDomain", "dnsPubkey", "key",
    )

    fun parse(link: String): Profile? = try {
        val l = link.trim().removePrefix("\uFEFF")
        when {
            l.startsWith("vless://", true) -> parseUriStyle(l, "vless")
            l.startsWith("trojan://", true) -> parseUriStyle(l, "trojan")
            l.startsWith("vmess://", true) -> parseVmess(l)
            l.startsWith("ss://", true) -> parseSs(l)
            l.startsWith("ssh://", true) -> parseSsh(l)
            l.startsWith("socks://", true) || l.startsWith("socks5://", true) -> parseSocks(l)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun looksLikeOvpn(t: String): Boolean =
        Regex("(?m)^\\s*remote\\s+\\S+").containsMatchIn(t) &&
            Regex("(?m)^\\s*(client|dev\\s|proto\\s)").containsMatchIn(t)

    /** Parses plain text, base64 subscription content or an .ovpn file into profiles. */
    fun parseSubscription(body: String): List<Profile> {
        val t = body.trim().removePrefix("\uFEFF")
        if (looksLikeOvpn(t)) return listOfNotNull(parseOvpn(t))
        val text = if (t.contains("://")) t else try { decodeB64(t) } catch (e: Exception) { t }
        return text.lines().mapNotNull { parse(it) }
    }

    private fun parseOvpn(t: String): Profile? {
        val m = Regex("(?m)^\\s*remote\\s+(\\S+)(?:\\s+(\\d+))?(?:\\s+(udp|tcp)\\S*)?").find(t) ?: return null
        val host = m.groupValues[1]
        val port = m.groupValues[2].toIntOrNull() ?: 1194
        val proto = m.groupValues[3].ifBlank {
            Regex("(?m)^\\s*proto\\s+(udp|tcp)").find(t)?.groupValues?.get(1) ?: "udp"
        }
        return Profile(
            name = "OpenVPN $host", protocol = "openvpn", address = host, port = port, secret = "",
            transport = proto, security = "tls", extra = JSONObject().put("config", t).toString(),
            country = CountryUtil.fromName(host),
        )
    }

    private fun parseUriStyle(link: String, proto: String): Profile? {
        val u = Uri.parse(link)
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else 443
        val extra = JSONObject()
        for (k in URI_KEYS) u.getQueryParameter(k)?.let { extra.put(k, it) }
        val name = u.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port"
        return Profile(
            name = name, protocol = proto, address = host.removePrefix("[").removeSuffix("]"), port = port,
            secret = u.userInfo ?: "",
            transport = u.getQueryParameter("type") ?: "tcp",
            security = u.getQueryParameter("security") ?: if (proto == "trojan") "tls" else "none",
            extra = extra.toString(), country = CountryUtil.fromName(name), rawLink = link,
        )
    }

    private fun parseVmess(link: String): Profile? {
        val j = JSONObject(decodeB64(link.trim().substring(8)))
        val extra = JSONObject()
        for (k in listOf("path", "host", "sni", "alpn", "fp", "type", "scy", "aid", "allowInsecure")) {
            j.optString(k).takeIf { it.isNotBlank() }?.let { extra.put(k, it) }
        }
        // vmess v2 puts the gRPC service name in "path"
        if (j.optString("net") == "grpc") extra.put("serviceName", j.optString("path"))
        val name = j.optString("ps").ifBlank { j.optString("add") }
        return Profile(
            name = name, protocol = "vmess", address = j.getString("add"),
            port = j.get("port").toString().trim().toInt(), secret = j.getString("id"),
            transport = j.optString("net", "tcp").ifBlank { "tcp" },
            security = j.optString("tls").ifBlank { "none" },
            extra = extra.toString(), country = CountryUtil.fromName(name), rawLink = link,
        )
    }

    private fun parseSs(link: String): Profile? {
        val body = link.trim().substring(5)
        val name = Uri.decode(body.substringAfter('#', ""))
        val noFrag = body.substringBefore('#')
        val query = noFrag.substringAfter('?', "")
        val main = noFrag.substringBefore('?').trimEnd('/')
        val userInfo: String
        val hostPort: String
        if (main.contains('@')) {
            val ui = main.substringBeforeLast('@')
            userInfo = if (ui.contains(':')) Uri.decode(ui) else decodeB64(Uri.decode(ui))
            hostPort = main.substringAfterLast('@')
        } else {
            val d = decodeB64(Uri.decode(main))
            userInfo = d.substringBeforeLast('@')
            hostPort = d.substringAfterLast('@')
        }
        val method = userInfo.substringBefore(':')
        val password = userInfo.substringAfter(':')
        val host = hostPort.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        val port = hostPort.substringAfterLast(':').trim().toInt()
        val extra = JSONObject().put("method", method.ifBlank { "aes-256-gcm" })

        var transport = "tcp"
        var security = "none"
        val plugin = query.split('&').firstOrNull { it.startsWith("plugin=") }
            ?.substringAfter("plugin=")?.let { Uri.decode(it) }.orEmpty()
        if (plugin.isNotBlank()) {
            val opts = plugin.split(';')
            val kv = opts.drop(1).associate { it.substringBefore('=') to it.substringAfter('=', "") }
            if (opts[0].contains("v2ray-plugin")) {
                transport = "ws"
                security = if (kv.containsKey("tls")) "tls" else "none"
                kv["host"]?.takeIf { it.isNotBlank() }?.let { extra.put("host", it) }
                extra.put("path", kv["path"]?.takeIf { it.isNotBlank() } ?: "/")
            } else {
                extra.put("unsupportedPlugin", opts[0])
            }
        }
        val n = name.ifBlank { "$host:$port" }
        return Profile(
            name = n, protocol = "ss", address = host, port = port, secret = password,
            transport = transport, security = security,
            extra = extra.toString(), country = CountryUtil.fromName(n), rawLink = link,
        )
    }

    private fun parseSsh(link: String): Profile? {
        val u = Uri.parse(link)
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else 22
        val ui = u.userInfo.orEmpty()
        val extra = JSONObject().put("user", ui.substringBefore(':').ifBlank { "root" })
        for (k in SSH_KEYS) u.getQueryParameter(k)?.let { extra.put(k, it) }
        if (!extra.has("mode")) extra.put("mode", "direct")
        val name = u.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port"
        return Profile(
            name = name, protocol = "ssh", address = host, port = port,
            secret = ui.substringAfter(':', ""), extra = extra.toString(),
            country = CountryUtil.fromName(name), rawLink = link,
        )
    }

    private fun parseSocks(link: String): Profile? {
        val u = Uri.parse(link)
        val host = u.host ?: return null
        val port = if (u.port > 0) u.port else 1080
        val ui = u.userInfo.orEmpty()
        val extra = JSONObject()
        if (ui.isNotBlank()) extra.put("user", ui.substringBefore(':'))
        val name = u.fragment?.takeIf { it.isNotBlank() } ?: "$host:$port"
        return Profile(
            name = name, protocol = "socks", address = host, port = port,
            secret = ui.substringAfter(':', ""), extra = extra.toString(),
            country = CountryUtil.fromName(name), rawLink = link,
        )
    }

    private fun decodeB64(s: String): String {
        var t = s.filter { !it.isWhitespace() }.replace('-', '+').replace('_', '/')
        while (t.length % 4 != 0) t += "="
        return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
    }
}

object CountryUtil {
    private val codes = mapOf(
        "united states" to "US", "usa" to "US", "germany" to "DE", "singapore" to "SG",
        "japan" to "JP", "united kingdom" to "GB", "uk" to "GB", "canada" to "CA",
        "france" to "FR", "netherlands" to "NL", "india" to "IN", "bangladesh" to "BD",
    )

    /** Returns ISO country code guessed from a server name, or "". */
    fun fromName(name: String): String {
        val n = name.lowercase()
        codes.forEach { (k, v) -> if (n.contains(k)) return v }
        Regex("\\b([A-Z]{2})\\b").find(name)?.value?.let { return it }
        // Flag emoji: two regional indicator symbols
        val cps = name.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (cps[i] in 0x1F1E6..0x1F1FF && cps[i + 1] in 0x1F1E6..0x1F1FF) {
                return "" + ('A' + (cps[i] - 0x1F1E6)) + ('A' + (cps[i + 1] - 0x1F1E6))
            }
        }
        return ""
    }

    fun flag(code: String): String {
        if (code.length != 2) return "\uD83C\uDF10"
        return code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}
