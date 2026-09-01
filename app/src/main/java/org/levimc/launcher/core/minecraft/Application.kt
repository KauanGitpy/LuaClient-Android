package org.levimc.launcher.core.minecraft

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.levimc.launcher.core.crash.CrashReporter
import org.levimc.launcher.core.lua.LuaLogManager
import org.levimc.launcher.core.lua.LuaNativeBridge
import org.levimc.launcher.core.news.NewsNotificationHelper
import org.levimc.launcher.settings.FeatureSettings
import org.levimc.launcher.ui.dialogs.LogcatOverlayManager

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        LuaLogManager.initialize(this)
        val nativeReady = LuaNativeBridge.initialize(this)
        LuaLogManager.record(
            "native",
            if (nativeReady) {
                "libLuaClient.so carregada; versao=${LuaNativeBridge.version()}; " +
                    "arquitetura=${LuaNativeBridge.architecture()}; " +
                    "render=${LuaNativeBridge.rendererSupport()}"
            } else {
                "libLuaClient.so indisponivel"
            }
        )
        FeatureSettings.init(applicationContext)
        CrashReporter.init(this)
        val processName = Application.getProcessName()
        if (processName.endsWith(":crash")) return

        LaunchSafetyManager.applyVersionSafeDefaults(this)

        NewsNotificationHelper.initialize(this)
        LogcatOverlayManager.init(this)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    companion object {
        @JvmStatic
        lateinit var context: Context
            private set

        @JvmStatic
        lateinit var preferences: SharedPreferences
            private set
    }
}
