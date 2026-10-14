package com.nora.tunnel.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-memory log shared by UI and (later) the VPN service. Cleared when the process dies. */
object AppLog {
    data class Entry(val time: Long, val type: String, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private var file: File? = null

    /** Keeps the log on disk, so it survives the process being killed (crash, native abort...). */
    fun init(ctx: Context) {
        try {
            val f = File(ctx.filesDir, "applog.txt")
            file = f
            if (f.exists()) {
                val old = f.readLines().takeLast(300).mapNotNull { l ->
                    val p = l.split('|', limit = 3)
                    if (p.size == 3) p[0].toLongOrNull()?.let { Entry(it, p[1], p[2]) } else null
                }.reversed()
                _entries.update { old + it }
            }
        } catch (_: Throwable) {}
    }

    /** type: CONNECT, DISCONNECT, ERROR or INFO */
    fun add(type: String, message: String) {
        val e = Entry(System.currentTimeMillis(), type, message)
        _entries.update { (listOf(e) + it).take(300) }
        try {
            val f = file ?: return
            synchronized(this) {
                if (f.length() > 100_000) f.writeText(f.readLines().takeLast(150).joinToString("\n", postfix = "\n"))
                f.appendText("${e.time}|${e.type}|${e.message.replace('\n', ' ')}\n")
            }
        } catch (_: Throwable) {}
    }

    fun clear() {
        _entries.value = emptyList()
        try { file?.delete() } catch (_: Throwable) {}
    }

    fun export(): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return _entries.value.joinToString("\n") { "${f.format(Date(it.time))} [${it.type}] ${it.message}" }
    }
}
