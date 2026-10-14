package com.nora.tunnel.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

object Net {
    /** TCP connect latency in ms, or null on failure/timeout. */
    suspend fun tcpPing(host: String, port: Int, timeoutMs: Int = 3000): Int? = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                val t = System.nanoTime()
                s.connect(InetSocketAddress(host, port), timeoutMs)
                ((System.nanoTime() - t) / 1_000_000).toInt()
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "NoraTunnel/1.0")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
