package com.nora.tunnel.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.ds by preferencesDataStore("nora_settings")

class SettingsStore(private val ctx: Context) {
    private val onboarding = booleanPreferencesKey("onboarding_done")
    private val theme = stringPreferencesKey("theme")
    private val autoConnect = booleanPreferencesKey("auto_connect")
    private val startOnBoot = booleanPreferencesKey("start_on_boot")
    private val killSwitch = booleanPreferencesKey("kill_switch")
    private val selectedProfile = longPreferencesKey("selected_profile")
    private val routing = stringPreferencesKey("routing_mode")

    val onboardingDone: Flow<Boolean> = ctx.ds.data.map { it[onboarding] ?: false }
    val themeMode: Flow<String> = ctx.ds.data.map { it[theme] ?: "dark" }
    val autoConnectOn: Flow<Boolean> = ctx.ds.data.map { it[autoConnect] ?: false }
    val startOnBootOn: Flow<Boolean> = ctx.ds.data.map { it[startOnBoot] ?: false }
    val killSwitchOn: Flow<Boolean> = ctx.ds.data.map { it[killSwitch] ?: false }

    val selectedProfileId: Flow<Long> = ctx.ds.data.map { it[selectedProfile] ?: -1L }
    val routingMode: Flow<String> = ctx.ds.data.map { it[routing] ?: "global" }

    suspend fun setSelectedProfileId(v: Long) = ctx.ds.edit { it[selectedProfile] = v }
    suspend fun setRoutingMode(v: String) = ctx.ds.edit { it[routing] = v }
    suspend fun setOnboardingDone(v: Boolean) = ctx.ds.edit { it[onboarding] = v }
    suspend fun setTheme(v: String) = ctx.ds.edit { it[theme] = v }
    suspend fun setAutoConnect(v: Boolean) = ctx.ds.edit { it[autoConnect] = v }
    suspend fun setStartOnBoot(v: Boolean) = ctx.ds.edit { it[startOnBoot] = v }
    suspend fun setKillSwitch(v: Boolean) = ctx.ds.edit { it[killSwitch] = v }

    private val notifications = booleanPreferencesKey("notifications")
    private val blockIpv6 = booleanPreferencesKey("block_ipv6")
    private val dns = stringPreferencesKey("dns")
    private val traffic = stringPreferencesKey("traffic")

    val notificationsOn: Flow<Boolean> = ctx.ds.data.map { it[notifications] ?: true }
    val blockIpv6On: Flow<Boolean> = ctx.ds.data.map { it[blockIpv6] ?: true }
    val dnsServer: Flow<String> = ctx.ds.data.map { it[dns] ?: "1.1.1.1" }
    val trafficJson: Flow<String> = ctx.ds.data.map { it[traffic] ?: "{}" }

    suspend fun setNotifications(v: Boolean) = ctx.ds.edit { it[notifications] = v }
    suspend fun setBlockIpv6(v: Boolean) = ctx.ds.edit { it[blockIpv6] = v }
    suspend fun setDns(v: String) = ctx.ds.edit { it[dns] = v }

    /** Called by the VPN core (next phase) to record traffic for today. */
    suspend fun addTraffic(rx: Long, tx: Long) = ctx.ds.edit {
        val o = JSONObject(it[traffic] ?: "{}")
        val k = LocalDate.now().toString()
        val a = o.optJSONArray(k)
        o.put(k, JSONArray().put((a?.optLong(0) ?: 0L) + rx).put((a?.optLong(1) ?: 0L) + tx))
        it[traffic] = o.toString()
    }
}
