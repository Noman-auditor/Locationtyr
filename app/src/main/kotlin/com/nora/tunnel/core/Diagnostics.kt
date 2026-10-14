package com.nora.tunnel.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

object Diagnostics {
    private val hostRegex = Regex("^[A-Za-z0-9.:\\-]{1,253}$")
    fun validHost(h: String) = hostRegex.matches(h)

    data class PingResult(val sent: Int, val received: Int, val times: List<Double>)

    private fun runPing(host: String, vararg extra: String): String {
        val cmd = listOf("/system/bin/ping") + extra + host
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(20, TimeUnit.SECONDS)) proc.destroyForcibly()
        return out
    }

    suspend fun ping(host: String, count: Int = 4): PingResult = withContext(Dispatchers.IO) {
        val out = runPing(host, "-c", "$count", "-W", "2")
        val times = Regex("time=([0-9.]+)").findAll(out).map { it.groupValues[1].toDouble() }.toList()
        PingResult(count, times.size, times)
    }

    /** Traceroute built from TTL-limited pings. Calls [onHop] with one formatted line per hop. */
    suspend fun traceroute(host: String, maxHops: Int = 20, onHop: (String) -> Unit) = withContext(Dispatchers.IO) {
        for (ttl in 1..maxHops) {
            val out = runPing(host, "-c", "1", "-t", "$ttl", "-W", "2")
            val reached = out.contains("bytes from")
            val ip = Regex("(?:From|from) ([0-9a-fA-F:.]+)").find(out)?.groupValues?.get(1)
            val ms = Regex("time=([0-9.]+)").find(out)?.groupValues?.get(1)
            onHop("%2d  %s  %s".format(ttl, ip ?: "*", ms?.let { "$it ms" } ?: ""))
            if (reached) break
        }
    }

    suspend fun dns(host: String): Pair<Long, List<String>> = withContext(Dispatchers.IO) {
        val t = System.nanoTime()
        val addrs = InetAddress.getAllByName(host).map { it.hostAddress ?: "" }
        ((System.nanoTime() - t) / 1_000_000) to addrs
    }

    suspend fun downloadMbps(bytes: Int = 10_000_000): Double = withContext(Dispatchers.IO) {
        val c = URL("https://speed.cloudflare.com/__down?bytes=$bytes").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            val t = System.nanoTime()
            var total = 0L
            val buf = ByteArray(65536)
            c.inputStream.use { while (true) { val n = it.read(buf); if (n < 0) break; total += n } }
            total * 8.0 / ((System.nanoTime() - t) / 1e9) / 1e6
        } finally { c.disconnect() }
    }

    suspend fun uploadMbps(bytes: Int = 3_000_000): Double = withContext(Dispatchers.IO) {
        val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.requestMethod = "POST"; c.doOutput = true
            c.setFixedLengthStreamingMode(bytes)
            val t = System.nanoTime()
            val chunk = ByteArray(65536)
            c.outputStream.use { o -> var left = bytes; while (left > 0) { val n = minOf(left, chunk.size); o.write(chunk, 0, n); left -= n } }
            c.responseCode
            bytes * 8.0 / ((System.nanoTime() - t) / 1e9) / 1e6
        } finally { c.disconnect() }
    }
}
