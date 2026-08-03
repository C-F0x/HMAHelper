package org.cf0x.hma.helper.preset

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

class PresetManager(context: Context) {

    init {
        PresetListLoader.init(context)
    }

    private val presetList = mutableListOf<BasePreset>()
    private val manifestDataCache = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "PresetManager"

        val PRESET_NAMES = listOf(
            XposedModulesPreset.NAME,
            EmbeddedXposedPreset.NAME,
            ManagerPreset.NAME,
            PrivilegedAppPreset.NAME,
            CustomROMPreset.NAME,
            AccessibilityAppsPreset.NAME,
        )
    }

    fun readManifest(packageName: String, zipFile: java.util.zip.ZipFile): String {
        if (Runtime.getRuntime().freeMemory() < 2048000) {
            manifestDataCache.clear()
            Log.v(TAG, "@readManifest cleared memory")
        }

        var cache = manifestDataCache[packageName]
        if (cache == null) {
            val manifestFile = zipFile.getInputStream(
                zipFile.getEntry("AndroidManifest.xml")
            )
            val manifestBytes = manifestFile.use { it.readBytes() }
            cache = String(manifestBytes, Charsets.US_ASCII)
            manifestDataCache[packageName] = cache
        }
        return cache
    }

    fun getPresetByName(name: String): BasePreset? = presetList.firstOrNull { it.name == name }

    fun getAllPresetCounts(): Map<String, Int> {
        return presetList.associate { it.name to it.packageNames.size }
    }

    fun getPresetPackages(name: String): Set<String> {
        val preset = getPresetByName(name) ?: return emptySet()
        return preset.packages
    }

    fun removeFromPreset(name: String, packages: Set<String>) {
        getPresetByName(name)?.packageNames?.removeAll(packages)
    }

    /** Manually add packages to a preset (used for non-manifest signals like libxposed providers). */
    fun addPackagesToPreset(name: String, packages: Set<String>) {
        val preset = getPresetByName(name) ?: return
        preset.packageNames.addAll(packages)
    }

    fun reloadPresets(appsList: List<ApplicationInfo>) {
        presetList.forEach { it.clearPackageList() }

        Log.i(TAG, "=== Starting scan: ${appsList.size} apps, ${presetList.size} presets ===")

        val matchCounts = mutableMapOf<String, Int>()
        var errorCount = 0

        for (appInfo in appsList) {
            if (appInfo.packageName == "android") continue
            // Skip runtime resource overlays (RRO) — not real apps.
            // 0x01000000 = ApplicationInfo.FLAG_IS_RESOURCE_OVERLAY (API 21+)
            if ((appInfo.flags and 0x01000000) != 0) continue

            presetList.forEach { preset ->
                runCatching {
                    if (preset.addPackageInfoPreset(appInfo)) {
                        matchCounts[preset.name] = (matchCounts[preset.name] ?: 0) + 1
                    }
                }.onFailure { e ->
                    errorCount++
                    Log.e(TAG, "Error checking ${appInfo.packageName} against ${preset.name}", e)
                }
            }
        }

        manifestDataCache.clear()
        Log.i(TAG, "=== Scan done: errors=$errorCount, matches=$matchCounts ===")
    }

    /**
     * Root-based scan. Does NOT clear existing matches — call this after
     * [reloadPresets] so root facts merge with (and complement) the
     * PackageManager-based results.
     */
    fun reloadPresetsRoot(rootApps: Map<String, RootAppInfo>) {
        Log.i(TAG, "=== Starting root scan: ${rootApps.size} apps ===")
        val matchCounts = mutableMapOf<String, Int>()

        for ((pkg, info) in rootApps) {
            if (pkg == "android") continue
            // Skip runtime resource overlays (RRO) — not real apps.
            if (info.isOverlay) continue

            presetList.forEach { preset ->
                runCatching {
                    if (preset.addPackageInfoRoot(pkg, info)) {
                        matchCounts[preset.name] = (matchCounts[preset.name] ?: 0) + 1
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Error checking $pkg against ${preset.name}", e)
                }
            }
        }
        Log.i(TAG, "=== Root scan done: matches=$matchCounts ===")
    }

    override fun toString(): String {
        return presetList.joinToString(", ") { "${it.name}=${it.packages.size}" }
    }

    init {
        presetList.add(XposedModulesPreset())
        presetList.add(EmbeddedXposedPreset())
        presetList.add(ManagerPreset())
        presetList.add(PrivilegedAppPreset())
        presetList.add(CustomROMPreset())
        presetList.add(AccessibilityAppsPreset(this))
    }
}
