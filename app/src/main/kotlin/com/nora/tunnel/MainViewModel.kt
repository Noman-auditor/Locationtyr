package com.nora.tunnel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nora.tunnel.core.AppLog
import com.nora.tunnel.core.LinkParser
import com.nora.tunnel.core.Net
import com.nora.tunnel.data.Profile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val nora = app as NoraApp
    private val dao = nora.db.profiles()
    val settings = nora.settings

    val profiles: StateFlow<List<Profile>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedId: StateFlow<Long> =
        settings.selectedProfileId.stateIn(viewModelScope, SharingStarted.Eagerly, -1L)
    val routingMode: StateFlow<String> =
        settings.routingMode.stateIn(viewModelScope, SharingStarted.Eagerly, "global")

    private val _pings = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val pings: StateFlow<Map<Long, Int>> = _pings

    fun select(id: Long) { viewModelScope.launch { settings.setSelectedProfileId(id) } }
    fun setRouting(mode: String) { viewModelScope.launch { settings.setRoutingMode(mode) } }

    fun delete(p: Profile) {
        viewModelScope.launch {
            dao.delete(p)
            if (selectedId.value == p.id) settings.setSelectedProfileId(-1L)
        }
    }

    /** Accepts share links (one per line), a base64/plain subscription body, or a subscription URL. */
    fun importText(text: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val t = text.trim()
            val msg = try {
                val isUrl = !t.contains('\n') && (t.startsWith("http://") || t.startsWith("https://"))
                val body = if (isUrl) Net.fetchText(t) else t
                val list = LinkParser.parseSubscription(body)
                val known = dao.observeAll().first().map { it.rawLink }.filter { it.isNotBlank() }.toSet()
                val fresh = list.filter { it.rawLink.isBlank() || it.rawLink !in known }
                fresh.forEach { dao.insert(it) }
                when {
                    list.isEmpty() -> "No supported profiles found"
                    fresh.isEmpty() -> "Already imported"
                    else -> "Imported ${fresh.size} profile(s)"
                }
            } catch (e: Exception) {
                "Import failed: ${e.message ?: "unsupported or unreadable content"}"
            }
            AppLog.add(if (msg.startsWith("Imported")) "INFO" else "ERROR", msg)
            onDone(msg)
        }
    }

    fun addProfile(p: Profile, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            dao.insert(p)
            AppLog.add("INFO", "Added ${p.name}")
            onDone()
        }
    }

    fun pingAll() {
        viewModelScope.launch {
            val res = coroutineScope {
                profiles.value.map { p -> async { p.id to Net.tcpPing(p.address, p.port) } }.awaitAll()
            }
            _pings.value = res.mapNotNull { (id, ms) -> ms?.let { id to it } }.toMap()
        }
    }
}
