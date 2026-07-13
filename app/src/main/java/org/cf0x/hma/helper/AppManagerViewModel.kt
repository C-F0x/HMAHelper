package org.cf0x.hma.helper

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.cf0x.hma.helper.data.dataStore
import org.cf0x.hma.helper.preset.PresetManager

data class AppInfo(
    val packageName: String,
    val appLabel: String,
    val isSystemApp: Boolean
)

// Per-app scope configuration model for the HMA JSON format
data class AppScopeConfig(
    val useWhitelist: Boolean = false,
    val aggressiveFilter: Boolean = false,
    val excludeSystemApps: Boolean = true,
    val enabledTemplates: List<String> = emptyList(),
    val extraAppList: List<String> = emptyList()
) {
    fun encode(): String {
        val templates = enabledTemplates.joinToString(",")
        val extra = extraAppList.joinToString(",")
        return "$useWhitelist|$aggressiveFilter|$excludeSystemApps|$templates|$extra"
    }

    companion object {
        fun decode(data: String): AppScopeConfig {
            val parts = data.split("|", limit = 5)
            if (parts.size < 5) return AppScopeConfig()
            return AppScopeConfig(
                useWhitelist = parts[0].toBooleanStrictOrNull() ?: false,
                aggressiveFilter = parts[1].toBooleanStrictOrNull() ?: false,
                excludeSystemApps = parts[2].toBooleanStrictOrNull() ?: true,
                enabledTemplates = parts[3].split(",").filter { it.isNotBlank() },
                extraAppList = parts[4].split(",").filter { it.isNotBlank() }
            )
        }
    }
}

data class Template(
    val name: String,
    val isWhitelist: Boolean = false,
    val appList: List<String> = emptyList()
) {
    fun encode(): String = "$name|$isWhitelist|${appList.joinToString(",")}"

    companion object {
        fun decode(data: String): Template {
            val parts = data.split("|", limit = 3)
            if (parts.size < 3) return Template(name = "")
            return Template(
                name = parts[0],
                isWhitelist = parts[1].toBooleanStrictOrNull() ?: false,
                appList = parts[2].split(",").filter { it.isNotBlank() }
            )
        }
    }
}

private val SCOPE_CONFIGS_KEY = stringPreferencesKey("scope_configs")
private val TEMPLATES_KEY = stringPreferencesKey("templates")

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _selectedTab = MutableStateFlow(TAB_USER)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _scopeConfigs = MutableStateFlow<Map<String, AppScopeConfig>>(emptyMap())
    private val _templates = MutableStateFlow<List<Template>>(emptyList())

    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()
    val selectedCount: StateFlow<Int> = _selectedPackages.map { it.size }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )
    val scopeConfigs: StateFlow<Map<String, AppScopeConfig>> = _scopeConfigs.asStateFlow()
    val templates: StateFlow<List<Template>> = _templates.asStateFlow()

    // Reactive filtered list
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _allApps, _selectedTab, _searchQuery, _selectedPackages
    ) { apps, tab, query, selected ->
        val q = query.lowercase().trim()
        val isUserTab = tab == TAB_USER
        apps.filter { app ->
            val tabMatch = if (isUserTab) !app.isSystemApp else app.isSystemApp
            tabMatch && (q.isEmpty() ||
                    app.packageName.lowercase().contains(q) ||
                    app.appLabel.lowercase().contains(q))
        }.sortedWith(
            compareByDescending<AppInfo> { it.packageName in selected }
                .thenBy { it.appLabel.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        const val TAB_USER = 0
        const val TAB_SYSTEM = 1
    }

    init {
        reload()
        loadScopeConfigs()
        loadTemplates()
    }

    fun setSelectedTab(tab: Int) { _selectedTab.value = tab }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun toggleSelection(packageName: String) {
        _selectedPackages.value = _selectedPackages.value.let { current ->
            if (packageName in current) current - packageName
            else current + packageName
        }
    }

    fun isSelected(packageName: String): Boolean = packageName in _selectedPackages.value
    fun clearSelection() { _selectedPackages.value = emptySet() }
    fun setSelection(packages: List<String>) { _selectedPackages.value = packages.toSet() }
    fun lastSelectedPackage(): String? = _selectedPackages.value.lastOrNull()

    fun getConfig(packageName: String): AppScopeConfig? = _scopeConfigs.value[packageName]

    fun saveConfig(packageName: String, config: AppScopeConfig) {
        _scopeConfigs.value = _scopeConfigs.value + (packageName to config)
        persistScopeConfigs()
    }

    fun removeConfig(packageName: String) {
        _scopeConfigs.value = _scopeConfigs.value - packageName
        persistScopeConfigs()
    }

    // ── Template Management ──

    fun getTemplates(): List<Template> = _templates.value

    fun addTemplate(template: Template) {
        _templates.value = _templates.value + template
        persistTemplates()
    }

    fun removeTemplate(name: String) {
        _templates.value = _templates.value.filter { it.name != name }
        persistTemplates()
    }

    fun updateTemplate(oldName: String, newTemplate: Template) {
        _templates.value = _templates.value.map { if (it.name == oldName) newTemplate else it }
        persistTemplates()
    }

    /** Returns the set of reserved template names: preset internal IDs + display labels (EN/CN) + existing templates */
    fun getReservedNames(): Set<String> {
        val enLabels = setOf(
            "Xposed Modules", "Embedded Xposed", "Managers",
            "Privileged Apps", "Custom ROM", "Accessibility Services"
        )
        val cnLabels = setOf(
            "Xposed 模块", "内嵌 Xposed 软件", "管理器",
            "特权软件", "自定义 ROM", "无障碍服务软件"
        )
        val existing = _templates.value.map { it.name }.toSet()
        return PresetManager.PRESET_NAMES.toSet() + enLabels + cnLabels + existing
    }

    // ── Persistence ──

    private fun loadScopeConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val raw = context.dataStore.data.map { prefs ->
                prefs[SCOPE_CONFIGS_KEY] ?: ""
            }.first()
            val map = mutableMapOf<String, AppScopeConfig>()
            if (raw.isNotBlank()) {
                raw.split("\n").filter { it.isNotBlank() }.forEach { line ->
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val pkg = line.substring(0, colonIdx)
                        val data = line.substring(colonIdx + 1)
                        map[pkg] = AppScopeConfig.decode(data)
                    }
                }
            }
            _scopeConfigs.value = map
        }
    }

    private fun persistScopeConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val raw = _scopeConfigs.value.entries.joinToString("\n") { (pkg, config) ->
                "$pkg:${config.encode()}"
            }
            context.dataStore.edit { prefs ->
                prefs[SCOPE_CONFIGS_KEY] = raw
            }
        }
    }

    private fun loadTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val raw = context.dataStore.data.map { prefs ->
                prefs[TEMPLATES_KEY] ?: ""
            }.first()
            val list = mutableListOf<Template>()
            if (raw.isNotBlank()) {
                raw.split("\n").filter { it.isNotBlank() }.forEach { line ->
                    val t = Template.decode(line)
                    if (t.name.isNotBlank()) list.add(t)
                }
            }
            _templates.value = list
        }
    }

    private fun persistTemplates() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val raw = _templates.value.joinToString("\n") { it.encode() }
            context.dataStore.edit { prefs ->
                prefs[TEMPLATES_KEY] = raw
            }
        }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val apps = runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .map { appInfo ->
                        val label = runCatching {
                            pm.getApplicationLabel(appInfo).toString()
                        }.getOrElse { appInfo.packageName }
                        AppInfo(
                            packageName = appInfo.packageName,
                            appLabel = label,
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    }
                    .sortedBy { it.appLabel.lowercase() }
            }.getOrDefault(emptyList())
            _allApps.value = apps
        }
    }
}
