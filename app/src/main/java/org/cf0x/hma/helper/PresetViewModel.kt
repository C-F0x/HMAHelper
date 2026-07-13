package org.cf0x.hma.helper

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.cf0x.hma.helper.data.dataStore
import org.cf0x.hma.helper.preset.PackageScanner
import org.cf0x.hma.helper.preset.PresetManager

private val SCOPE_CONFIGS_KEY = stringPreferencesKey("scope_configs")

data class PresetAppItem(
    val packageName: String,
    val appLabel: String,
    val isInstalled: Boolean
)

class PresetViewModel(application: Application) : AndroidViewModel(application) {

    private val presetManager = PresetManager(application)

    private val _presetCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val presetCounts: StateFlow<Map<String, Int>> = _presetCounts.asStateFlow()

    private val _presetAppList = MutableStateFlow<Map<String, List<PresetAppItem>>>(emptyMap())
    val presetAppList: StateFlow<Map<String, List<PresetAppItem>>> = _presetAppList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _scopeCount = MutableStateFlow(0)
    val scopeCount: StateFlow<Int> = _scopeCount.asStateFlow()

    val presetNames = PresetManager.PRESET_NAMES

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            val context = getApplication<Application>()
            val pm = context.packageManager

            // --- Step 1: standard bulk query ---
            val stdApps = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                .mapNotNull { it.applicationInfo }

            // --- Step 2: cross-path bulk scan for hidden packages ---
            val scannedNames = PackageScanner.scan(context, emptySet())
            val stdNames = stdApps.map { it.packageName }.toSet()
            val missingNames = scannedNames - stdNames
            Log.i("PresetVM", "Std=${stdNames.size}, Scanned=${scannedNames.size}, Missing=${missingNames.size}")

            // --- Step 3: get ApplicationInfo for missing packages ---
            val extraApps = missingNames.mapNotNull { pkg ->
                runCatching {
                    pm.getApplicationInfo(pkg, 0)
                }.getOrNull()
            }

            // --- Step 4: merge ---
            val allApps = (stdApps + extraApps).distinctBy { it.packageName }

            // --- Step 5: run presets ---
            presetManager.reloadPresets(allApps)

            // --- Step 6: dedup ---
            val xposedPkgs = presetManager.getPresetPackages("xposed")
            presetManager.removeFromPreset("embedded_xposed", xposedPkgs)

            // --- Step 6.5: load scope config count ---
            val rawScope = context.dataStore.data.first()[SCOPE_CONFIGS_KEY] ?: ""
            _scopeCount.value = rawScope.split("\n").count { it.isNotBlank() }

            _presetCounts.value = presetManager.getAllPresetCounts()

            // --- Step 7: build display list ---
            val installedSet = stdNames + scannedNames // union: everything visible through any path
            val appListMap = mutableMapOf<String, List<PresetAppItem>>()
            presetNames.forEach { name ->
                val allPackages = presetManager.getPresetPackages(name)
                val items = allPackages.map { pkg ->
                    val installed = pkg in installedSet
                    val label = if (installed) {
                        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                            .getOrDefault(pkg)
                    } else pkg
                    PresetAppItem(
                        packageName = pkg,
                        appLabel = label,
                        isInstalled = installed
                    )
                }.sortedWith(
                    compareByDescending<PresetAppItem> { it.isInstalled }
                        .thenBy { it.appLabel.lowercase() }
                )
                appListMap[name] = items
            }
            _presetAppList.value = appListMap

            _isLoading.value = false
        }
    }

    fun getDisplayLabelRes(presetName: String): Int {
        return when (presetName) {
            "xposed" -> R.string.preset_name_xposed
            "embedded_xposed" -> R.string.preset_name_embedded_xposed
            "managers" -> R.string.preset_name_managers
            "privileged_apps" -> R.string.preset_name_privileged_apps
            "custom_rom" -> R.string.preset_name_custom_rom
            "accessibility_apps" -> R.string.preset_name_accessibility_apps
            else -> 0
        }
    }

    fun getSubtitleRes(presetName: String): Int {
        return when (presetName) {
            "xposed" -> R.string.preset_subtitle_xposed
            "embedded_xposed" -> R.string.preset_subtitle_embedded_xposed
            "managers" -> R.string.preset_subtitle_managers
            "privileged_apps" -> R.string.preset_subtitle_privileged_apps
            "custom_rom" -> R.string.preset_subtitle_custom_rom
            "accessibility_apps" -> R.string.preset_subtitle_accessibility_apps
            else -> 0
        }
    }
}
