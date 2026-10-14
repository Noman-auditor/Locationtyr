package com.nora.tunnel.core

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.nora.tunnel.data.Profile
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** True when the profile is an SSH profile that rides on a local DNSTT tunnel. */
fun Profile.isDnstt(): Boolean = protocol == "ssh" && try {
    JSONObject(extra.ifBlank { "{}" }).optString("mode") == "dnstt"
} catch (e: Exception) { false }

/** Settings of an SSH profile. Modes: direct, payload, proxy, tls, ws, dnstt. */
class SshConfig(p: Profile, resolvedIp: String) {
    private val x = JSONObject(p.extra.ifBlank { "{}" })
    val mode: String = x.optString("mode", "direct").ifBlank { "direct" }
    val user: String = x.optString("user", "root").ifBlank { "root" }
    val password: String = p.secret
    val key: String = x.optString("key")
    val payload: String = x.optString("payload")
    val proxyHost: String = x.optString("proxyHost")
    val proxyPort: Int = x.optString("proxyPort").toIntOrNull() ?: 8080
    val sni: String = x.optString("sni")
    val insecure: Boolean = x.optString("insecure").let { it == "1" || it.equals("true", true) }
    val tlsFlag: Boolean = x.optString("tls").let { it == "1" || it.equals("true", true) }
    val wsPath: String = x.optString("wsPath").ifBlank { "/" }
    val wsHost: String = x.optString("wsHost")
    val dnsMode: String = x.optString("dnsMode", "udp")
    val dnsServer: String = x.optString("dnsServer")
    val dnsDomain: String = x.optString("dnsDomain")
    val dnsPubkey: String = x.optString("dnsPubkey")

    val serverName: String = p.address
    val connectHost: String = if (mode == "dnstt") "127.0.0.1" else resolvedIp
    val connectPort: Int = if (mode == "dnstt") SshEngine.DNSTT_PORT else p.port
    val useTls: Boolean = mode == "tls" || ((mode == "payload" || mode == "ws") && tlsFlag)
}

object SshEngine {
    const val DNSTT_PORT = 17000

    private var session: Session? = null
    private var socks: SocksServer? = null
    private var dnstt: Process? = null

    fun isAlive(): Boolean {
        val d = dnstt
        return session?.isConnected == true && (d == null || d.isAlive)
    }

    /** Blocking: call from an IO thread. Throws on any failure, leaving nothing running. */
    fun start(ctx: Context, p: Profile, resolvedIp: String) {
        stop()
        try {
            val c = SshConfig(p, resolvedIp)
            if (c.mode == "dnstt") startDnstt(ctx, c)

            val jsch = JSch()
            if (c.key.isNotBlank()) {
                jsch.addIdentity("tyt-key", c.key.toByteArray(), null,
                    if (c.password.isNotEmpty()) c.password.toByteArray() else null)
            }
            val s = jsch.getSession(c.user, c.serverName, c.connectPort)
            if (c.password.isNotEmpty()) s.setPassword(c.password)
            // Host keys are not pinned (typical for tunnel servers behind SNI/CDN fronts).
            s.setConfig("StrictHostKeyChecking", "no")
            s.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password")
            s.setServerAliveInterval(15_000)
            s.setServerAliveCountMax(3)
            s.setSocketFactory(TunnelFactory(c))
            s.connect(25_000)
            session = s

            val srv = SocksServer(s, XrayConfig.SOCKS_PORT)
            srv.start()
            socks = srv
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    fun stop() {
        try { socks?.stop() } catch (_: Exception) {}
        socks = null
        try { session?.disconnect() } catch (_: Exception) {}
        session = null
        try { dnstt?.destroy() } catch (_: Exception) {}
        dnstt = null
    }

    // ---------- DNSTT ----------

    private fun startDnstt(ctx: Context, c: SshConfig) {
        val bin = File(ctx.applicationInfo.nativeLibraryDir, "libdnstt.so")
        if (!bin.exists()) throw IllegalStateException("DNSTT binary (libdnstt.so) not found (see NATIVE.md)")
        if (c.dnsServer.isBlank() || c.dnsDomain.isBlank() || c.dnsPubkey.isBlank()) {
            throw IllegalStateException("DNSTT needs a resolver, a domain and the server public key")
        }
        val flag = when (c.dnsMode) { "doh" -> "-doh"; "dot" -> "-dot"; else -> "-udp" }
        val pb = ProcessBuilder(
            bin.absolutePath, flag, c.dnsServer, "-pubkey", c.dnsPubkey, c.dnsDomain, "127.0.0.1:$DNSTT_PORT",
        ).redirectErrorStream(true)
        // Go has no system CA bundle on Android; point it at the platform one.
        pb.environment()["SSL_CERT_DIR"] = "/system/etc/security/cacerts"
        val proc = pb.start()
        dnstt = proc
        Thread {
            try { proc.inputStream.bufferedReader().forEachLine { AppLog.add("INFO", "dnstt: $it") } } catch (_: Exception) {}
        }.also { it.isDaemon = true }.start()

        val end = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < end) {
            if (!proc.isAlive) throw IllegalStateException("dnstt exited (code ${proc.exitValue()})")
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", DNSTT_PORT), 500) }
                return
            } catch (_: Exception) { Thread.sleep(300) }
        }
        throw IllegalStateException("dnstt did not start listening")
    }

    // ---------- transport layering: TCP -> (TLS) -> (proxy/payload) -> SSH ----------

    private class TunnelFactory(private val c: SshConfig) : SocketFactory {
        private val streams = ConcurrentHashMap<Socket, InputStream>()

        override fun createSocket(host: String, port: Int): Socket {
            var s = if (c.mode == "proxy") {
                tcp(c.proxyHost.ifBlank { throw IOException("HTTP proxy host is empty") }, c.proxyPort)
            } else {
                tcp(c.connectHost, c.connectPort)
            }
            try {
                if (c.useTls) s = wrapTls(s)
                val text = payloadText()
                var pin: InputStream = s.getInputStream()
                if (text != null) pin = exchange(s, text)
                streams[s] = pin
                return s
            } catch (e: Exception) {
                try { s.close() } catch (_: Exception) {}
                throw e
            }
        }

        override fun getInputStream(socket: Socket): InputStream = streams[socket] ?: socket.getInputStream()
        override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

        private fun tcp(h: String, p: Int): Socket {
            val s = Socket()
            s.tcpNoDelay = true
            s.keepAlive = true
            s.connect(InetSocketAddress(h, p), 12_000)
            return s
        }

        @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
        private object TrustAll : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        private fun wrapTls(raw: Socket): Socket {
            val peer = c.sni.ifBlank { c.serverName }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, if (c.insecure) arrayOf<TrustManager>(TrustAll) else null, SecureRandom())
            val ss = ctx.socketFactory.createSocket(raw, peer, raw.port, true) as SSLSocket
            val params = ss.sslParameters
            if (c.sni.isNotBlank() && !c.sni.all { it.isDigit() || it == '.' || it == ':' }) {
                params.serverNames = listOf(SNIHostName(c.sni))
            }
            if (!c.insecure) params.endpointIdentificationAlgorithm = "HTTPS"
            ss.sslParameters = params
            ss.startHandshake()
            return ss
        }

        /** Text to send before the SSH banner, or null for none. */
        private fun payloadText(): String? {
            val hp = "${c.serverName}:${c.connectPort}"
            return when (c.mode) {
                "proxy" -> if (c.payload.isNotBlank()) expand(c.payload) else "CONNECT $hp HTTP/1.1\r\nHost: $hp\r\n\r\n"
                "payload" -> if (c.payload.isNotBlank()) expand(c.payload) else null
                "ws" -> if (c.payload.isNotBlank()) expand(c.payload) else {
                    val k = Base64.encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
                    "GET ${c.wsPath} HTTP/1.1\r\nHost: ${c.wsHost.ifBlank { c.sni.ifBlank { c.serverName } }}\r\n" +
                        "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $k\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"
                }
                else -> null
            }
        }

        private fun expand(raw: String): String = raw
            .replace("[crlf]", "\r\n", true).replace("[cr]", "\r", true).replace("[lf]", "\n", true)
            .replace("[host_port]", "${c.serverName}:${c.connectPort}", true)
            .replace("[host]", c.serverName, true).replace("[port]", c.connectPort.toString(), true)
            .replace("[protocol]", "HTTP/1.1", true)
            .replace("\\r", "\r").replace("\\n", "\n")

        /** Sends [text] (split at [split]) and swallows the HTTP reply, leaving SSH bytes untouched. */
        private fun exchange(s: Socket, text: String): InputStream {
            val out = s.getOutputStream()
            val parts = text.split("[split]", ignoreCase = true)
            parts.forEachIndexed { i, part ->
                out.write(part.toByteArray(Charsets.ISO_8859_1)); out.flush()
                if (i < parts.size - 1) Thread.sleep(300)
            }
            val pin = PushbackInputStream(s.getInputStream(), 16)
            val old = s.soTimeout
            s.soTimeout = 6_000
            try {
                val first = ByteArray(4)
                readFully(pin, first)
                if (String(first, Charsets.ISO_8859_1) == "SSH-") { pin.unread(first); return pin }
                val sb = StringBuilder(String(first, Charsets.ISO_8859_1))
                while (!(sb.endsWith("\r\n\r\n") || sb.endsWith("\n\n"))) {
                    val b = pin.read()
                    if (b < 0) throw IOException("Connection closed during payload exchange")
                    sb.append(b.toChar())
                    if (sb.length > 16_384) throw IOException("Oversized reply to payload")
                }
                val line = sb.lineSequence().first().trim()
                val code = Regex("HTTP/\\S+\\s+(\\d{3})").find(line)?.groupValues?.get(1)?.toInt()
                if (code != null && code >= 400) throw IOException("Server replied: $line")
            } catch (_: SocketTimeoutException) {
                // Server stayed silent (waiting for us): carry on with SSH.
            } finally {
                s.soTimeout = old
            }
            return pin
        }
    }

    private fun readFully(i: InputStream, b: ByteArray) {
        var n = 0
        while (n < b.size) {
            val r = i.read(b, n, b.size - n)
            if (r < 0) throw IOException("EOF")
            n += r
        }
    }

    // ---------- local SOCKS5 server that forwards through the SSH session ----------

    private class SocksServer(private val session: Session, private val port: Int) {
        private val pool: ExecutorService = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }
        private var server: ServerSocket? = null
        @Volatile private var running = false

        fun start() {
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 128)
            server = ss
            running = true
            pool.execute {
                while (running && !ss.isClosed) {
                    try {
                        val c = ss.accept()
                        pool.execute { handle(c) }
                    } catch (_: Exception) { if (ss.isClosed) break }
                }
            }
        }

        fun stop() {
            running = false
            try { server?.close() } catch (_: Exception) {}
            pool.shutdownNow()
        }

        private fun reply(out: OutputStream, code: Int) {
            out.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0)); out.flush()
        }

        private fun handle(c: Socket) {
            try {
                c.tcpNoDelay = true
                val inp = DataInputStream(BufferedInputStream(c.getInputStream()))
                val out = c.getOutputStream()
                if (inp.readUnsignedByte() != 5) return
                val n = inp.readUnsignedByte()
                inp.readFully(ByteArray(n))
                out.write(byteArrayOf(5, 0)); out.flush()
                if (inp.readUnsignedByte() != 5) return
                val cmd = inp.readUnsignedByte()
                inp.readUnsignedByte()
                val host = when (inp.readUnsignedByte()) {
                    1 -> ByteArray(4).also { inp.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
                    3 -> ByteArray(inp.readUnsignedByte()).also { inp.readFully(it) }.let { String(it) }
                    4 -> ByteArray(16).also { inp.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
                    else -> { reply(out, 8); return }
                }
                val dport = inp.readUnsignedShort()
                when (cmd) {
                    1 -> connect(c, inp, out, host, dport)
                    3 -> udp(inp, out)
                    else -> reply(out, 7)
                }
            } catch (_: Exception) {
            } finally {
                try { c.close() } catch (_: Exception) {}
            }
        }

        private fun connect(c: Socket, inp: InputStream, out: OutputStream, host: String, port: Int) {
            val ch = session.getStreamForwarder(host, port)
            val rin = ch.inputStream
            val rout = ch.outputStream
            try {
                ch.connect(10_000)
            } catch (e: Exception) {
                reply(out, 5); try { ch.disconnect() } catch (_: Exception) {}; return
            }
            reply(out, 0)
            pool.execute {
                try { inp.copyTo(rout) } catch (_: Exception) {}
                try { rout.close() } catch (_: Exception) {}
            }
            try { rin.copyTo(out) } catch (_: Exception) {}
            try { ch.disconnect() } catch (_: Exception) {}
            try { c.close() } catch (_: Exception) {}
        }

        /** UDP ASSOCIATE: only DNS (port 53) is carried, as DNS-over-TCP through the SSH session. */
        private fun udp(inp: InputStream, out: OutputStream) {
            val ds = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
            val lp = ds.localPort
            out.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, (lp shr 8).toByte(), lp.toByte())); out.flush()
            pool.execute {
                val buf = ByteArray(4096)
                while (!ds.isClosed) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try { ds.receive(pkt) } catch (_: Exception) { break }
                    val data = pkt.data.copyOfRange(pkt.offset, pkt.offset + pkt.length)
                    val client = InetSocketAddress(pkt.address, pkt.port)
                    pool.execute { try { dnsDatagram(ds, client, data) } catch (_: Exception) {} }
                }
            }
            try { while (inp.read() >= 0) { /* wait for control connection to close */ } } catch (_: Exception) {}
            ds.close()
        }

        private fun dnsDatagram(ds: DatagramSocket, client: InetSocketAddress, d: ByteArray) {
            if (d.size < 10 || d[2].toInt() != 0) return
            var i = 4
            val host = when (d[3].toInt()) {
                1 -> { val h = InetAddress.getByAddress(d.copyOfRange(i, i + 4)).hostAddress!!; i += 4; h }
                3 -> { val l = d[i].toInt() and 0xff; val h = String(d, i + 1, l); i += 1 + l; h }
                4 -> { val h = InetAddress.getByAddress(d.copyOfRange(i, i + 16)).hostAddress!!; i += 16; h }
                else -> return
            }
            val dport = ((d[i].toInt() and 0xff) shl 8) or (d[i + 1].toInt() and 0xff)
            i += 2
            if (dport != 53) return
            val header = d.copyOfRange(0, i)
            val resp = dnsOverSsh(host, d.copyOfRange(i, d.size)) ?: return
            val reply = header + resp
            ds.send(DatagramPacket(reply, reply.size, client))
        }

        private fun dnsOverSsh(host: String, q: ByteArray): ByteArray? {
            val ch = session.getStreamForwarder(host, 53)
            val rin = ch.inputStream
            val rout = ch.outputStream
            ch.connect(5_000)
            try {
                val f = pool.submit(Callable<ByteArray?> {
                    rout.write(byteArrayOf((q.size shr 8).toByte(), q.size.toByte()))
                    rout.write(q); rout.flush()
                    val h = ByteArray(2); readFully(rin, h)
                    val len = ((h[0].toInt() and 0xff) shl 8) or (h[1].toInt() and 0xff)
                    ByteArray(len).also { readFully(rin, it) }
                })
                return try { f.get(6, TimeUnit.SECONDS) } catch (e: Exception) { f.cancel(true); null }
            } finally {
                try { ch.disconnect() } catch (_: Exception) {}
            }
        }
    }
}
