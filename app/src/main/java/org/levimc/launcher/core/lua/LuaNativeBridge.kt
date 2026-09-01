package org.levimc.launcher.core.lua

import android.content.Context
import android.util.Log

object LuaNativeBridge {
    private const val TAG = "LuaNativeBridge"

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loaded = false

    @JvmStatic
    fun initialize(context: Context): Boolean {
        if (loaded && nativeIsInitialized()) return true
        synchronized(this) {
            if (!loadAttempted) {
                loadAttempted = true
                loaded = try {
                    System.loadLibrary("LuaClient")
                    true
                } catch (error: Throwable) {
                    Log.e(TAG, "Falha ao carregar libLuaClient.so", error)
                    false
                }
            }
            if (!loaded) return false
            return try {
                nativeInitialize(context.filesDir.absolutePath)
            } catch (error: Throwable) {
                Log.e(TAG, "Falha ao inicializar o nucleo nativo", error)
                false
            }
        }
    }

    @JvmStatic
    fun isReady(): Boolean = loaded && try {
        nativeIsInitialized()
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    fun version(): String = if (loaded) safeNativeValue { nativeGetVersion() } else "indisponivel"

    @JvmStatic
    fun architecture(): String = if (loaded) safeNativeValue { nativeGetArchitecture() } else "indisponivel"

    @JvmStatic
    fun rendererSupport(): String = if (loaded) safeNativeValue { nativeGetRendererSupport() } else "indisponivel"

    private inline fun safeNativeValue(block: () -> String): String = try {
        block()
    } catch (_: Throwable) {
        "indisponivel"
    }

    @JvmStatic private external fun nativeInitialize(dataDirectory: String): Boolean
    @JvmStatic private external fun nativeIsInitialized(): Boolean
    @JvmStatic private external fun nativeGetVersion(): String
    @JvmStatic private external fun nativeGetArchitecture(): String
    @JvmStatic private external fun nativeGetRendererSupport(): String
}
