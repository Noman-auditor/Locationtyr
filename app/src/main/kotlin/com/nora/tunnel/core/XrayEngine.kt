package com.nora.tunnel.core

import android.content.Context
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Talks to the Xray AAR (gomobile "libv2ray" package) through reflection, so the project compiles
 * without it. Expected API (AndroidLibXrayLite):
 *   Libv2ray.initCoreEnv(String assetsPath, String key)          [optional]
 *   Libv2ray.newCoreController(CoreCallbackHandler) -> CoreController
 *   CoreController.startLoop(String configJson, int tunFd), stopLoop(), getIsRunning()
 */
object XrayEngine {
    private const val ENTRY = "libv2ray.Libv2ray"
    private const val HANDLER = "libv2ray.CoreCallbackHandler"
    private var controller: Any? = null

    /** Loads (and initialises) the AAR class; false if missing or its gojni native lib can't load. */
    fun isAvailable(): Boolean = try { Class.forName(ENTRY); true } catch (e: Throwable) { false }

    private fun find(cls: Class<*>, name: String) =
        cls.methods.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun start(ctx: Context, configJson: String) {
        try {
            startInternal(ctx, configJson)
        } catch (e: InvocationTargetException) {
            val t = e.targetException ?: e
            throw IllegalStateException(t.message ?: "Xray failed to start", t)
        } catch (e: LinkageError) {
            throw IllegalStateException("Xray native library failed to load: ${e.message}", e)
        }
    }

    private fun startInternal(ctx: Context, configJson: String) {
        val entry = Class.forName(ENTRY)
        find(entry, "initCoreEnv")?.invoke(null, ctx.filesDir.absolutePath, "")

        val handlerCls = Class.forName(HANDLER)
        val handler = Proxy.newProxyInstance(handlerCls.classLoader, arrayOf(handlerCls)) { _, m, _ ->
            when (m.returnType) {
                java.lang.Long.TYPE -> 0L
                java.lang.Integer.TYPE -> 0
                java.lang.Boolean.TYPE -> false
                else -> null
            }
        }
        val factory = find(entry, "newCoreController")
            ?: throw IllegalStateException("Unsupported Xray AAR: newCoreController not found (see XrayEngine.kt)")
        val c = factory.invoke(null, handler) ?: throw IllegalStateException("Xray controller is null")
        val startLoop = find(c.javaClass, "startLoop")
            ?: throw IllegalStateException("Unsupported Xray AAR: startLoop not found (see XrayEngine.kt)")
        // tunFd = 0: traffic arrives through the local SOCKS inbound (hev-socks5-tunnel feeds it).
        if (startLoop.parameterTypes.size == 2) startLoop.invoke(c, configJson, 0) else startLoop.invoke(c, configJson)
        controller = c
    }

    fun stop() {
        val c = controller ?: return
        try { find(c.javaClass, "stopLoop")?.invoke(c) } catch (_: Exception) {}
        controller = null
    }
}
