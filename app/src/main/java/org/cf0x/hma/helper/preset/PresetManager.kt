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
            System.gc()
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

    fun reloadPresets(appsList: List<ApplicationInfo>) {
        presetList.forEach { it.clearPackageList() }

        Log.i(TAG, "=== Starting scan: ${appsList.size} apps, ${presetList.size} presets ===")

        val matchCounts = mutableMapOf<String, Int>()
        var errorCount = 0

        for (appInfo in appsList) {
            if (appInfo.packageName == "android") continue

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
