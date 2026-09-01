package org.levimc.launcher.core.minecraft

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import org.levimc.launcher.core.mods.Mod
import org.levimc.launcher.core.mods.ModManager
import org.levimc.launcher.core.mods.ModNativeLoader
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager
import org.levimc.launcher.core.mods.inbuilt.model.ModIds
import org.levimc.launcher.core.versions.GameVersion
import org.levimc.launcher.preloader.PreloaderInput
import org.levimc.launcher.preloader.PreloaderSignatureRulesManager
import org.levimc.launcher.util.LauncherStorage
import java.io.File

object MinecraftRuntimePreparer {
    const val EXTRA_COMPATIBILITY_SAFE_MODE =
        "org.levimc.launcher.extra.COMPATIBILITY_SAFE_MODE"

    data class PreparedRuntime(
        val version: GameVersion?,
        val gameManager: GamePackageManager,
        val skippedIncompatibleMods: List<String> = emptyList()
    )

    interface ProgressListener {
        fun onProgress(progress: Int, status: String, detail: String? = null)
        fun onLog(message: String)
    }

    @JvmStatic
    @JvmName("nativeSetupRuntime")
    private external fun nativeSetupRuntime(modsPath: String)

    private val noopListener = object : ProgressListener {
        override fun onProgress(progress: Int, status: String, detail: String?) = Unit
        override fun onLog(message: String) = Unit
    }

    fun prepare(
        context: Context,
        launchIntent: Intent,
        listener: ProgressListener = noopListener
    ): PreparedRuntime {
        val trace = LaunchTrace.ensure(launchIntent)
        trace.milestone("Runtime preparation started")
        listener.onProgress(4, "Checking selected version")
        val version = resolveGameVersion(launchIntent)
            ?: throw IllegalArgumentException("No Minecraft version specified")
        listener.onLog("Using ${version.directoryName} (${version.versionCode})")
        trace.mark("Minecraft version resolved", "${version.directoryName} ${version.versionCode}")

        val compatibilitySafeMode = shouldUseGxCoreCompatibilitySafeMode(
            version.versionCode,
            Build.VERSION.SDK_INT
        )
        launchIntent.putExtra(EXTRA_COMPATIBILITY_SAFE_MODE, compatibilitySafeMode)
        if (compatibilitySafeMode) {
            listener.onLog(
                "Compatibility safe mode enabled: skipping gxcore and additional native mods " +
                    "on Android ${Build.VERSION.SDK_INT} with Minecraft ${version.versionCode}"
            )
            trace.warning(
                "Compatibility safe mode enabled",
                "gxcore and additional native mods disabled for Android ${Build.VERSION.SDK_INT} / Minecraft ${version.versionCode}"
            )
        }

        listener.onProgress(12, "Preparing game files")
        val gameManager = GamePackageManager.getInstance(context.applicationContext, version, trace, null)
        trace.mark("GamePackageManager ready")

        listener.onProgress(26, "Preparing launch")
        prepareMinecraftIntent(context, launchIntent, gameManager, version)
        trace.mark("Launch intent prepared")

        listener.onProgress(34, "Checking mods")
        val modManager = ModManager.getInstance()
        modManager.setCurrentVersion(version)
        trace.mark("ModManager state prepared")

        listener.onProgress(40, "Preparing game loader")
        listener.onLog("Loading game loader")
        trace.mark("Game loader load started")
        if (ModManager.ensurePreloaderLoaded()) {
            trace.mark("Game loader load finished")
        } else {
            trace.mark("Game loader load skipped", "preloader unavailable")
        }
        val signatureRulesFile = PreloaderSignatureRulesManager.getRulesFile(context.applicationContext)
        PreloaderInput.configureSignatureRules(signatureRulesFile, version.versionCode)
        trace.mark("Preloader signature rules configured", signatureRulesFile?.absolutePath ?: "<none>")

        listener.onLog("Loading native libraries")
        loadMinecraftLibraries(gameManager, version, compatibilitySafeMode, listener, trace)

        listener.onProgress(78, "Loading enabled mods")
        listener.onLog("Loading native mods")

        val skippedIncompatibleMods = if (compatibilitySafeMode) {
            listener.onLog("Skipped additional native mods in compatibility safe mode")
            trace.mark(
                "Native mod loading skipped",
                "compatibility safe mode keeps only Java overlays and input bridge"
            )
            emptyList()
        } else {
            try {
                org.levimc.launcher.core.mods.inbuilt.nativemod.InbuiltModsNative.loadLibrary()
                val gyroEnabled = InbuiltModManager.getInstance(context)
                    .resolveInbuiltModEnabled(ModIds.GYRO, false)
                if (gyroEnabled) {
                    org.levimc.launcher.core.mods.inbuilt.nativemod.GyroMod.nativePreResolve()
                } else {
                    listener.onLog("Skipping disabled gyro native hook")
                }
            } catch (_: Throwable) {}

            //nativeSetupRuntime(modManager.currentVersion?.modsDir?.absolutePath.toString())
            loadNativeMods(context, launchIntent, modManager, listener, trace)
        }

        listener.onProgress(100, "Runtime ready", "Entering Minecraft")
        trace.milestone("Runtime preparation finished")
        return PreparedRuntime(version, gameManager, skippedIncompatibleMods)
    }

    @JvmStatic
    fun resolveGameVersion(intent: Intent): GameVersion? {
        val parcelableVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(MinecraftLauncher.EXTRA_GAME_VERSION, GameVersion::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<GameVersion>(MinecraftLauncher.EXTRA_GAME_VERSION)
        }
        if (parcelableVersion != null) {
            return parcelableVersion
        }

        val versionDir = intent.getStringExtra("MC_PATH")
        val versionCode = intent.getStringExtra("MINECRAFT_VERSION") ?: ""
        val versionDirName = intent.getStringExtra("MINECRAFT_VERSION_DIR") ?: ""
        val isInstalled = intent.getBooleanExtra("IS_INSTALLED", false)

        return if (!versionDir.isNullOrEmpty()) {
            GameVersion(
                versionDirName,
                versionCode,
                versionCode,
                File(versionDir),
                isInstalled,
                MinecraftLauncher.MC_PACKAGE_NAME,
                ""
            )
        } else if (versionCode.isNotEmpty()) {
            GameVersion(
                versionDirName,
                versionCode,
                versionCode,
                null,
                isInstalled,
                MinecraftLauncher.MC_PACKAGE_NAME,
                ""
            )
        } else {
            null
        }
    }

    private fun prepareMinecraftIntent(
        context: Context,
        launchIntent: Intent,
        gameManager: GamePackageManager,
        version: GameVersion
    ) {
        val profileId = MinecraftLauncher.getStorageProfileId(version)
        val versionIsolation = version.versionIsolation
        val filesDir = LauncherStorage.getStorageFilesRoot(context, profileId, versionIsolation, false)
        val externalFilesDir = LauncherStorage.getStorageFilesRoot(context, profileId, versionIsolation, true)
        val dataDir = LauncherStorage.getStorageDataRoot(context, profileId, versionIsolation)
        val cacheDir = LauncherStorage.getStorageCacheRoot(context, profileId, versionIsolation)

        version.versionDir?.let { launchIntent.putExtra("MC_PATH", it.absolutePath) }
        launchIntent.putExtra("IS_INSTALLED", version.isInstalled)
        launchIntent.putExtra("VERSION_ISOLATION", versionIsolation)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_PROFILE_ID, profileId)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_FILES_DIR, filesDir.absolutePath)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_EXTERNAL_FILES_DIR, externalFilesDir.absolutePath)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_DATA_DIR, dataDir.absolutePath)
        launchIntent.putExtra(MinecraftLauncher.EXTRA_STORAGE_CACHE_DIR, cacheDir.absolutePath)

        val mcInfo: ApplicationInfo = if (version.isInstalled) {
            gameManager.getPackageContext().applicationInfo
        } else {
            MinecraftLauncher(context).createFakeApplicationInfo(version, MinecraftLauncher.MC_PACKAGE_NAME)
        }
        launchIntent.putExtra("MC_SRC", mcInfo.sourceDir)
        val splitSourceDirs = mcInfo.splitSourceDirs
        if (splitSourceDirs != null) {
            launchIntent.putExtra("MC_SPLIT_SRC", arrayListOf(*splitSourceDirs))
        }
        launchIntent.putExtra("MINECRAFT_VERSION", version.versionCode)
        launchIntent.putExtra("MINECRAFT_VERSION_DIR", version.directoryName)
        launchIntent.putExtra("LAUNCH_VERTICALLY", version.launchVertically)
        launchIntent.putExtra("VERSION_ISOLATION", version.versionIsolation)
    }

    private fun loadMinecraftLibraries(
        gameManager: GamePackageManager,
        version: GameVersion,
        compatibilitySafeMode: Boolean,
        listener: ProgressListener,
        trace: LaunchTrace
    ) {
        listener.onProgress(46, "Loading native libraries")
        trace.mark("Minecraft library loading started")

        if (shouldLoadHttpClient(version)) {
            loadLibrary(gameManager, "c++_shared", 48, true, listener, trace)
            loadLibrary(gameManager, "HttpClient.Android", 52, true, listener, trace)
        }

        if (shouldLoadMaesdk(version)) {
            val excludeLibs = HashSet<String>()
            val excludeReasons = HashMap<String, String>()
            if (shouldLoadHttpClient(version)) {
                excludeLibs.add("c++_shared")
                excludeLibs.add("HttpClient.Android")
                excludeReasons["c++_shared"] = "already loaded before the bundle"
                excludeReasons["HttpClient.Android"] = "already loaded before the bundle"
            }
            if (!shouldLoadPlayFab(version)) {
                excludeLibs.add("PlayFabMultiplayer")
                excludeReasons["PlayFabMultiplayer"] = "not required by this Minecraft version"
            }
            if (compatibilitySafeMode) {
                excludeLibs.add("gxcore")
                excludeReasons["gxcore"] =
                    "disabled after a confirmed native crash on Android 15 with Minecraft 1.26.45.x"
            }
            listener.onProgress(56, "Loading native libraries")
            trace.mark("Minecraft native library bundle loading started", "1.21.110+ layout")
            val failedLibraries = gameManager
                .loadAllLibraries(excludeLibs, trace, listener, 56, 74, excludeReasons)
                .filterNot { it.loaded }
            if (failedLibraries.isNotEmpty()) {
                val details = failedLibraries.joinToString(separator = "\n") { result ->
                    "${result.fileName}: ${result.detail ?: "unknown error"}"
                }
                trace.error("Native library bundle load failed", details)
                throw RuntimeException("Failed to load native libraries:\n$details")
            }
            trace.mark("Minecraft native library bundle loading finished")
        } else {
            if (!shouldLoadHttpClient(version)) {
                loadLibrary(gameManager, "c++_shared", 50, true, listener, trace)
            }
            loadLibrary(gameManager, "fmod", 56, true, listener, trace)
            loadLibrary(gameManager, "MediaDecoders_Android", 62, true, listener, trace)
            loadLibrary(gameManager, "minecraftpe", 70, true, listener, trace)
            if (compatibilitySafeMode) {
                listener.onLog("Skipped native library: libgxcore.so")
                trace.mark(
                    "Native library load skipped",
                    "libgxcore.so - Android 15 / Minecraft 1.26.45.x compatibility safe mode"
                )
            } else {
                loadLibrary(gameManager, "gxcore", 74, true, listener, trace)
            }
        }
        trace.mark("Minecraft library loading finished")
    }

    @JvmStatic
    fun shouldUseGxCoreCompatibilitySafeMode(versionCode: String?, sdkInt: Int): Boolean {
        if (sdkInt < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val normalizedVersion = versionCode?.trim().orEmpty()
        return normalizedVersion == "1.26.45" || normalizedVersion.startsWith("1.26.45.")
    }

    private fun loadLibrary(
        gameManager: GamePackageManager,
        name: String,
        progress: Int,
        required: Boolean,
        listener: ProgressListener,
        trace: LaunchTrace
    ) {
        val fileName = toLibraryFileName(name)
        listener.onProgress(progress, "Loading native libraries", fileName)
        listener.onLog("Loading native library: $fileName")
        trace.mark("Native library load started", fileName)
        val result = gameManager.loadLibraryDetailed(name)
        if (!result.loaded && required) {
            listener.onLog("Failed to load native library: ${result.fileName}")
            trace.error(
                "Required library load failed",
                "${result.fileName} in ${result.durationMs}ms from ${result.source}" +
                    (result.detail?.let { " - $it" } ?: "")
            )
            throw RuntimeException("Failed to load ${result.fileName}: ${result.detail ?: "unknown error"}")
        }
        if (result.loaded) {
            listener.onLog("Loaded native library: ${result.fileName}")
            trace.mark(
                "Native library load finished",
                "${result.fileName} in ${result.durationMs}ms from ${result.source}" +
                    (result.detail?.let { " - $it" } ?: "")
            )
        } else {
            listener.onLog("Skipped native library: ${result.fileName}")
            trace.mark(
                "Native library load skipped",
                "${result.fileName} in ${result.durationMs}ms from ${result.source}" +
                    (result.detail?.let { " - $it" } ?: "")
            )
        }
    }

    private fun loadNativeMods(
        context: Context,
        launchIntent: Intent,
        modManager: ModManager,
        listener: ProgressListener,
        trace: LaunchTrace
    ): List<String> {
        val cacheDir = resolveNativeModCacheDir(context, launchIntent)
        trace.mark(
            "Native mod loading started",
            "mods=${modManager.currentVersion?.modsDir?.absolutePath ?: "<unknown>"}"
        )
        val modLoadLabels = java.util.IdentityHashMap<Mod, String>()
        val skippedIncompatibleMods = mutableListOf<String>()
        ModNativeLoader.loadEnabledSoMods(
            modManager,
            cacheDir,
            object : ModNativeLoader.LoadListener {
                override fun onScanStarted(totalEnabled: Int) {
                    if (totalEnabled > 0) {
                        listener.onLog("Loading $totalEnabled enabled mod(s)")
                    } else {
                        listener.onLog("No enabled native mods")
                    }
                }

                override fun onModLoadStarted(mod: Mod, index: Int, total: Int) {
                    val progress = 80 + ((index - 1) * 15 / total.coerceAtLeast(1))
                    val label = "$index/$total"
                    modLoadLabels[mod] = label
                    listener.onProgress(progress, "Loading native mods", "$label ${mod.displayName}")
                    trace.mark("Native mod load started", "$label ${mod.displayName}")
                }

                override fun onModLoadFinished(mod: Mod) {
                    val label = modLoadLabels.remove(mod)?.let { "$it " }.orEmpty()
                    listener.onLog("Loaded mod: $label${mod.displayName}")
                    trace.mark("Native mod load finished", mod.displayName)
                }

                override fun onModLoadSkipped(mod: Mod, minecraftVersion: String) {
                    val label = modLoadLabels.remove(mod)?.let { "$it " }.orEmpty()
                    skippedIncompatibleMods.add(mod.displayName)
                    listener.onLog("Skipped incompatible mod ${label}${mod.displayName} for Minecraft $minecraftVersion")
                    trace.warning("Native mod skipped as incompatible", "${mod.displayName}: $minecraftVersion")
                }

                override fun onModLoadFailed(mod: Mod, error: Throwable) {
                    trace.warning("Native mod load failed", "${mod.displayName}: ${error.message ?: error.javaClass.simpleName}")
                    listener.onLog("Failed to load mod ${mod.displayName}: ${error.message ?: error.javaClass.simpleName}")
                }

                override fun onMessage(message: String) {
                    listener.onLog(message)
                    trace.warning("Native mod loader message", message)
                }
            }
        )
        listener.onProgress(96, "Native mods ready")
        listener.onLog("Native mods ready")
        trace.mark("Native mod loading finished")
        return skippedIncompatibleMods
    }

    private fun resolveNativeModCacheDir(context: Context, launchIntent: Intent): File {
        val versionDirName = launchIntent.getStringExtra("MINECRAFT_VERSION_DIR")
            ?.takeIf { it.isNotBlank() }
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?: "default"
        return File(context.cacheDir, "native_mods/$versionDirName").also { it.mkdirs() }
    }

    private fun shouldLoadMaesdk(version: GameVersion): Boolean {
        val versionCode = version.versionCode
        val targetVersion = if (versionCode.contains("beta")) "1.21.110.22" else "1.21.110"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun shouldLoadHttpClient(version: GameVersion): Boolean {
        val versionCode = version.versionCode
        val targetVersion = if (versionCode.contains("beta")) "1.21.130.20" else "1.21.130"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun shouldLoadPlayFab(version: GameVersion): Boolean {
        val versionCode = version.versionCode
        val targetVersion = if (versionCode.contains("beta")) "1.21.130.20" else "1.21.130"
        return isVersionAtLeast(versionCode, targetVersion)
    }

    private fun toLibraryFileName(name: String): String {
        return if (name.startsWith("lib") && name.endsWith(".so")) name else "lib${name.removePrefix("lib").removeSuffix(".so")}.so"
    }

    private fun isVersionAtLeast(currentVersion: String, targetVersion: String): Boolean {
        return try {
            val current = currentVersion.replace(Regex("[^0-9.]"), "").split(".")
            val target = targetVersion.split(".")
            val maxLength = maxOf(current.size, target.size)

            for (i in 0 until maxLength) {
                val currentPart = current.getOrNull(i)?.toIntOrNull() ?: 0
                val targetPart = target.getOrNull(i)?.toIntOrNull() ?: 0

                if (currentPart > targetPart) return true
                if (currentPart < targetPart) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
