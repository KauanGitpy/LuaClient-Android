package org.levimc.launcher.core.minecraft;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.levimc.launcher.core.lua.LuaLogManager;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

import java.util.Locale;

/**
 * Persists the last launch stage so a native process abort can be diagnosed
 * after Android starts the launcher again.
 */
public final class LaunchSafetyManager {
    private static final String PREFS_NAME = "lua_launch_safety";
    private static final String KEY_SAFE_DEFAULTS_VERSION = "safe_defaults_version";
    private static final String KEY_PENDING = "launch_pending";
    private static final String KEY_REPORTED = "launch_reported";
    private static final String KEY_VERSION = "minecraft_version";
    private static final String KEY_STAGE = "last_stage";
    private static final String KEY_STARTED_AT = "started_at";
    private static final int SAFE_DEFAULTS_VERSION = 102;

    private LaunchSafetyManager() {
    }

    public static void applyVersionSafeDefaults(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getInt(KEY_SAFE_DEFAULTS_VERSION, 0) >= SAFE_DEFAULTS_VERSION) return;

        InbuiltModManager manager = InbuiltModManager.getInstance(context);
        manager.setModMenuEnabled(true);
        manager.setInbuiltModEnabled(ModIds.CPS_DISPLAY, true);

        String[] nativeOrVersionSensitiveModules = {
                ModIds.QUICK_DROP,
                ModIds.CAMERA_PERSPECTIVE,
                ModIds.TOGGLE_HUD,
                ModIds.ZOOM,
                ModIds.FPS_DISPLAY,
                ModIds.SNAPLOOK,
                ModIds.VIRTUAL_CURSOR,
                ModIds.GYRO,
                ModIds.POJAV_CONTROLS,
                ModIds.MORE_BUTTONS,
                ModIds.HOTBAR_SLOT
        };
        for (String moduleId : nativeOrVersionSensitiveModules) {
            manager.setInbuiltModEnabled(moduleId, false);
        }

        prefs.edit().putInt(KEY_SAFE_DEFAULTS_VERSION, SAFE_DEFAULTS_VERSION).apply();
        LuaLogManager.record("safety", "Modo seguro v0.1.2 aplicado; CPS mantido e hooks nativos desativados");
    }

    public static void markLaunchStarted(Context context, String minecraftVersion) {
        prefs(context).edit()
                .putBoolean(KEY_PENDING, true)
                .putBoolean(KEY_REPORTED, false)
                .putString(KEY_VERSION, safe(minecraftVersion))
                .putString(KEY_STAGE, "preparacao iniciada")
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .apply();
        recordDevice(context, minecraftVersion);
    }

    public static void markStage(Context context, String stage) {
        prefs(context).edit().putString(KEY_STAGE, safe(stage)).apply();
    }

    public static void markStable(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_PENDING, false)
                .putBoolean(KEY_REPORTED, false)
                .putString(KEY_STAGE, "jogo estavel")
                .apply();
        LuaLogManager.record("safety", "Minecraft permaneceu aberto; inicializacao marcada como estavel");
    }

    public static void markHandledFailure(Context context, String stage) {
        prefs(context).edit()
                .putBoolean(KEY_PENDING, false)
                .putString(KEY_STAGE, safe(stage))
                .apply();
    }

    public static String consumeUnexpectedExitReport(Context context) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_PENDING, false) || prefs.getBoolean(KEY_REPORTED, false)) {
            return null;
        }
        prefs.edit().putBoolean(KEY_REPORTED, true).apply();
        applyVersionSafeDefaults(context);

        String version = prefs.getString(KEY_VERSION, "desconhecida");
        String stage = prefs.getString(KEY_STAGE, "desconhecida");
        long startedAt = prefs.getLong(KEY_STARTED_AT, 0L);
        long elapsedSeconds = startedAt > 0L
                ? Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L)
                : 0L;
        String report = String.format(Locale.US,
                "A inicializacao anterior terminou inesperadamente.\n\nMinecraft: %s\nUltima etapa: %s\nTempo antes do fechamento: %ds\n\nOs modulos nativos foram desativados. Exporte o log antes de tentar novamente.",
                version, stage, elapsedSeconds);
        LuaLogManager.record("safety", report.replace('\n', ' '));
        return report;
    }

    private static void recordDevice(Context context, String minecraftVersion) {
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "desconhecida";
        LuaLogManager.record(
                "device",
                "modelo=" + Build.MANUFACTURER + " " + Build.MODEL
                        + "; Android=" + Build.VERSION.RELEASE
                        + " API=" + Build.VERSION.SDK_INT
                        + "; ABI=" + abi
                        + "; Minecraft=" + safe(minecraftVersion)
        );
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "desconhecida" : value.trim();
    }
}
