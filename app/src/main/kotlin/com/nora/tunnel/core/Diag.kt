package com.nora.tunnel.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Crash diagnostics that survive process death, so the real reason shows up in Logs on the next launch.
 *  - Java/Kotlin crashes: stack trace saved by an uncaught-exception handler.
 *  - Native crashes (SIGSEGV/abort in hev or the Go runtime): detected via the "stage" breadcrumb
 *    plus Android's recorded process exit reason.
 */
object Diag {
    private const val PREFS = "diag"

    private val _last = MutableStateFlow<String?>(null)
    /** Human-readable description of how the previous run died, if it did. */
    val lastReport: StateFlow<String?> = _last
    fun dismiss() { _last.value = null }
    private fun note(lines: MutableList<String>, s: String) { lines.add(s); AppLog.add("ERROR", s) }

    fun install(app: android.app.Application) {
        val file = File(app.filesDir, "last_crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { file.writeText("Thread: ${t.name}\n" + Log.getStackTraceString(e)) } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    /** Marks what the VPN service is doing. "busy:*" values left behind mean the process died there. */
    fun stage(ctx: Context, s: String) {
        try { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("stage", s).commit() } catch (_: Throwable) {}
    }

    /** Pulls readable strings (library names, abort message) out of Android's native-crash tombstone. */
    private fun nativeTrace(info: ApplicationExitInfo, lines: MutableList<String>) {
        try {
            val bytes = info.traceInputStream?.use { it.readBytes() } ?: return
            val text = String(bytes, Charsets.ISO_8859_1)
            Regex("[\\x20-\\x7e]{8,}").findAll(text).map { it.value.trim() }
                .filter { s -> s.contains(".so") || s.contains("Abort", true) || s.contains("signal", true) || s.contains("SIG") }
                .distinct().take(12).forEach { note(lines, "native: " + it.take(160)) }
        } catch (_: Throwable) {}
    }

    fun report(app: android.app.Application) {
        try {
            val lines = mutableListOf<String>()
            val file = File(app.filesDir, "last_crash.txt")
            if (file.exists()) {
                note(lines, "Previous app crash (Java/Kotlin):")
                file.readText().lines().filter { it.isNotBlank() }.take(14).forEach { note(lines, it.trim()) }
                file.delete()
            }
            val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stage = sp.getString("stage", "idle").orEmpty()
            if (stage.startsWith("busy:")) {
                note(lines, "App process died while: ${stage.removePrefix("busy:")}")
            }
            sp.edit().putString("stage", "idle").commit()
            if (Build.VERSION.SDK_INT >= 30) {
                val am = app.getSystemService(ActivityManager::class.java)
                val last = am.getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull()
                val normal = setOf(ApplicationExitInfo.REASON_EXIT_SELF, ApplicationExitInfo.REASON_USER_REQUESTED,
                    ApplicationExitInfo.REASON_OTHER, ApplicationExitInfo.REASON_USER_STOPPED)
                if (last != null && (stage.startsWith("busy:") || last.reason !in normal)) {
                    val why = when (last.reason) {
                        ApplicationExitInfo.REASON_CRASH -> "Java crash"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE crash (hev / Xray library)"
                        ApplicationExitInfo.REASON_SIGNALED -> "killed by signal ${last.status}"
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "killed: low memory"
                        ApplicationExitInfo.REASON_ANR -> "ANR (app not responding)"
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed: excessive resource usage"
                        else -> "reason code ${last.reason}"
                    }
                    note(lines, "Android exit reason: $why ${last.description.orEmpty()}")
                    if (last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) nativeTrace(last, lines)
                }
            }
            if (lines.isNotEmpty()) _last.value = lines.joinToString("\n")
        } catch (_: Throwable) {}
    }
}
