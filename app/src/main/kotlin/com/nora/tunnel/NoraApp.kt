package com.nora.tunnel

import android.app.Application
import com.nora.tunnel.data.AppDb
import com.nora.tunnel.data.SettingsStore

class NoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.nora.tunnel.core.AppLog.init(this)
        com.nora.tunnel.core.Diag.install(this)
        com.nora.tunnel.core.Diag.report(this)
    }

    val db by lazy { AppDb.get(this) }
    val settings by lazy { SettingsStore(this) }
}
