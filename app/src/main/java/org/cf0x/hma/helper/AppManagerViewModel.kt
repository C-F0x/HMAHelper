package org.cf0x.hma.helper

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.cf0x.hma.helper.data.AppSettings
import org.cf0x.hma.helper.data.dataStore
import org.cf0x.hma.helper.preset.PresetManager
import org.cf0x.hma.helper.root.HmaConfigManager
import org.cf0x.hma.helper.root.RootShell
import org.cf0x.hma.helper.ui.components.Md3Toast
import org.json.JSONObject

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

enum class ScopeChangeOp { ADD, REMOVE, MODIFY }
enum class TemplateChangeOp { ADD, REMOVE, MODIFY }

/** Describes what changed before a successful HMA config push (for toasts). */
data class HmaChange(
    val scopeOp: ScopeChangeOp? = null,
    val scopePkgs: List<String> = emptyList(),
    val templateOp: TemplateChangeOp? = null,
    val templateName: String? = null
)

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

    /**
     * Optional full-HMA-JSON builder injected by the UI layer (it needs
     * access to preset scan results and app settings). When set and the
     * user has enabled ROOT mode + "manage HMA config", every persisted
     * change is pushed straight into HMA's config.json.
     */
    var hmaJsonBuilder: (() -> String)? = null

    /**
     * Latest HMA sync event (StateFlow: survives navigation — the home screen
     * shows the restart prompt when it re-subscribes). Toasts are shown
     * directly from the ViewModel, so they appear on ANY screen.
     */
    private val _hmaSyncEvent = MutableStateFlow<HmaChange?>(null)
    val hmaSyncEvent: StateFlow<HmaChange?> = _hmaSyncEvent.asStateFlow()

    fun consumeHmaSyncEvent() { _hmaSyncEvent.value = null }

    // Pending change accumulation for batched ops (e.g. multi-select edit loops
    // calling saveConfig N times) — merged into ONE push + ONE toast after a
    // short debounce window.
    private var pendingScopeOp: ScopeChangeOp? = null
    private val pendingScopePkgs = mutableListOf<String>()
    private var pendingTemplateOp: TemplateChangeOp? = null
    private var pendingTemplateName: String? = null
    private var hmaPushJob: kotlinx.coroutines.Job? = null

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

    /** Reset picker-only UI state (tab/search) when entering a fresh picker. */
    fun resetPickerState() {
        _selectedTab.value = TAB_USER
        _searchQuery.value = ""
    }

    fun getConfig(packageName: String): AppScopeConfig? = _scopeConfigs.value[packageName]

    fun saveConfig(packageName: String, config: AppScopeConfig) {
        val isNew = packageName !in _scopeConfigs.value
        _scopeConfigs.value = _scopeConfigs.value + (packageName to config)
        persistScopeConfigs()
        recordScopeChange(if (isNew) ScopeChangeOp.ADD else ScopeChangeOp.MODIFY, packageName)
    }

    /**
     * Persist without recording an HMA change — used for transient saves that
     * are not user edits (e.g. the "Select extra apps" round-trip).
     */
    fun saveConfigSilent(packageName: String, config: AppScopeConfig) {
        _scopeConfigs.value = _scopeConfigs.value + (packageName to config)
        persistScopeConfigs()
    }

    fun removeConfig(packageName: String) {
        _scopeConfigs.value = _scopeConfigs.value - packageName
        persistScopeConfigs()
        recordScopeChange(ScopeChangeOp.REMOVE, packageName)
    }

    // ── Template Management ──

    fun getTemplates(): List<Template> = _templates.value

    fun addTemplate(template: Template) {
        _templates.value = _templates.value + template
        persistTemplates()
        recordTemplateChange(TemplateChangeOp.ADD, template.name)
    }

    fun removeTemplate(name: String) {
        _templates.value = _templates.value.filter { it.name != name }
        persistTemplates()
        // Remove dangling references from scope configs.
        if (_scopeConfigs.value.any { (_, c) -> name in c.enabledTemplates }) {
            _scopeConfigs.value = _scopeConfigs.value.mapValues { (_, c) ->
                if (name in c.enabledTemplates) c.copy(enabledTemplates = c.enabledTemplates - name)
                else c
            }
            persistScopeConfigs()
        }
        recordTemplateChange(TemplateChangeOp.REMOVE, name)
    }

    fun updateTemplate(oldName: String, newTemplate: Template) {
        _templates.value = _templates.value.map { if (it.name == oldName) newTemplate else it }
        persistTemplates()
        // Rename rewrites scope references to the old name.
        if (oldName != newTemplate.name) {
            _scopeConfigs.value = _scopeConfigs.value.mapValues { (_, c) ->
                if (oldName in c.enabledTemplates) c.copy(enabledTemplates = c.enabledTemplates.map { if (it == oldName) newTemplate.name else it })
                else c
            }
            persistScopeConfigs()
        }
        recordTemplateChange(TemplateChangeOp.MODIFY, newTemplate.name)
    }

    /** Returns the set of reserved template names: preset IDs + display labels (EN/CN) + prefixed forms + existing templates */
    fun getReservedNames(): Set<String> {
        val context = getApplication<Application>()
        val enLabels = setOf(
            "Xposed Modules", "Embedded Xposed", "Managers",
            "Privileged Apps", "Custom ROM", "Accessibility Services"
        )
        val cnLabels = setOf(
            "Xposed 模块", "内嵌 Xposed 软件", "管理器",
            "特权软件", "自定义 ROM", "无障碍服务软件"
        )
        val existing = _templates.value.map { it.name }.toSet()
        // Prefixed built-in names must also be reserved, or a custom template
        // could shadow an exported preset template.
        val prefixed = PresetNaming.allPrefixedNames(context).keys
        return PresetManager.PRESET_NAMES.toSet() + enLabels + cnLabels + existing + prefixed
    }

    // ── Persistence ──

    // Serializes DataStore writes: batched ops (e.g. import, multi-edit) spawn
    // many persist coroutines; without a mutex an older snapshot could win the
    // race and overwrite newer data.
    private val persistMutex = Mutex()

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
            persistMutex.withLock {
                val context = getApplication<Application>()
                val raw = _scopeConfigs.value.entries.joinToString("\n") { (pkg, config) ->
                    "$pkg:${config.encode()}"
                }
                context.dataStore.edit { prefs ->
                    prefs[SCOPE_CONFIGS_KEY] = raw
                }
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
            persistMutex.withLock {
                val context = getApplication<Application>()
                val raw = _templates.value.joinToString("\n") { it.encode() }
                context.dataStore.edit { prefs ->
                    prefs[TEMPLATES_KEY] = raw
                }
            }
        }
    }

    // ── HMA config takeover (ROOT mode) ──

    private fun recordScopeChange(op: ScopeChangeOp, pkg: String) {
        synchronized(pendingScopePkgs) {
            pendingScopeOp = op
            // Deduplicate: the same package may be saved twice in one burst
            // (e.g. picker round-trips), which would inflate the batch count.
            if (pkg !in pendingScopePkgs) pendingScopePkgs.add(pkg)
        }
        scheduleHmaPush()
    }

    private fun recordTemplateChange(op: TemplateChangeOp, name: String) {
        synchronized(pendingScopePkgs) {
            pendingTemplateOp = op
            pendingTemplateName = name
        }
        scheduleHmaPush()
    }

    private fun clearPending() {
        synchronized(pendingScopePkgs) {
            pendingScopeOp = null
            pendingScopePkgs.clear()
            pendingTemplateOp = null
            pendingTemplateName = null
        }
    }

    private fun hasPendingChange(): Boolean = synchronized(pendingScopePkgs) {
        pendingScopeOp != null || pendingTemplateOp != null
    }

    /**
     * Debounced batched push: waits for the current burst of changes, builds
     * one HMA JSON, writes it via root, and emits a single HmaChange event.
     */
    private fun scheduleHmaPush() {
        if (hmaPushJob?.isActive == true) return
        hmaPushJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            val app = getApplication<Application>()
            val settings = AppSettings(app)
            val manage = runCatching { settings.manageHmaConfig.first() }.getOrDefault(false)
            val builder = hmaJsonBuilder
            val change: HmaChange
            synchronized(pendingScopePkgs) {
                change = HmaChange(
                    scopeOp = pendingScopeOp,
                    scopePkgs = pendingScopePkgs.toList(),
                    templateOp = pendingTemplateOp,
                    templateName = pendingTemplateName
                )
                clearPending()
            }
            if (!manage || builder == null || !RootShell.isRootAvailable()) return@launch
            val json = runCatching { builder() }.getOrNull() ?: return@launch
            if (HmaConfigManager.writeConfig(json)) {
                showHmaChangeToasts(app, change)
                _hmaSyncEvent.value = change
            }
            // Changes made while writeConfig was running (the debounce window
            // had already fired) would otherwise sit unconsumed — push them now.
            if (hasPendingChange()) {
                scheduleHmaPush()
            }
        }
    }

    /** Immediate push (used when enabling the takeover switch in Settings). No toasts. */
    fun pushToHmaNow() {
        val builder = hmaJsonBuilder ?: return
        if (!RootShell.isRootAvailable()) return

        viewModelScope.launch(Dispatchers.IO) {
            val json = runCatching { builder() }.getOrNull() ?: return@launch
            if (HmaConfigManager.writeConfig(json)) {
                _hmaSyncEvent.value = HmaChange()
            }
        }
    }

    /** Toast summaries for the pushed change — shown from any screen via app context. */
    private suspend fun showHmaChangeToasts(app: Application, change: HmaChange) {
        val texts = mutableListOf<String>()
        change.scopeOp?.let { op ->
            val target = if (change.scopePkgs.size == 1)
                change.scopePkgs[0]
            else
                app.getString(R.string.hma_toast_n_apps, change.scopePkgs.size)
            val verb = when (op) {
                ScopeChangeOp.ADD -> app.getString(R.string.hma_action_add_scope)
                ScopeChangeOp.REMOVE -> app.getString(R.string.hma_action_remove_scope)
                ScopeChangeOp.MODIFY -> app.getString(R.string.hma_action_modify_scope)
            }
            texts += "$verb $target"
        }
        change.templateOp?.let { op ->
            val name = change.templateName ?: return@let
            val verb = when (op) {
                TemplateChangeOp.ADD -> app.getString(R.string.hma_action_add_template)
                TemplateChangeOp.REMOVE -> app.getString(R.string.hma_action_remove_template)
                TemplateChangeOp.MODIFY -> app.getString(R.string.hma_action_modify_template)
            }
            texts += "$verb $name"
        }
        if (texts.isEmpty()) return
        // Toast must be created on a thread with a Looper (the main thread).
        withContext(Dispatchers.Main) {
            texts.forEach { Md3Toast.show(app, it) }
        }
    }

    /**
     * Atomic full import (JSON → scope configs + templates). Runs on the
     * ViewModel scope so it survives navigation away from the import dialog,
     * and replaces the collections in one shot (single persist each) instead
     * of per-item CRUD — no partial state, no per-item HMA push noise.
     */
    fun importJson(
        jsonStr: String,
        appSettings: AppSettings,
        onScopeRemoved: (Int) -> Unit,
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                val json = JSONObject(jsonStr)

                // Misc config with defaults
                appSettings.saveConfigVersion(json.optInt("configVersion", 93))
                appSettings.saveDetailLog(json.optBoolean("detailLog", false))
                appSettings.saveMaxLogSize(json.optInt("maxLogSize", 512))
                appSettings.saveForceMountData(json.optBoolean("forceMountData", true))
                appSettings.saveAggressiveFilter(json.optBoolean("aggressiveFilter", false))

                // Custom templates only (prefixed built-in presets are skipped)
                val newTemplates = mutableListOf<Template>()
                if (json.has("templates")) {
                    val tObj = json.getJSONObject("templates")
                    tObj.keys().forEach { name ->
                        if (PresetNaming.resolveToId(app, name) != null) return@forEach
                        // Skip names that would corrupt the DataStore encoding.
                        if (name.any { it == '|' || it == ',' || it == ':' || it == '\n' || it == '\r' || it == '/' || it == '?' || it == '#' || it == '%' }) return@forEach
                        val t = tObj.getJSONObject(name)
                        val appList = mutableListOf<String>()
                        val arr = t.getJSONArray("appList")
                        for (i in 0 until arr.length()) appList.add(arr.getString(i))
                        newTemplates += Template(
                            name = name,
                            isWhitelist = t.getBoolean("isWhitelist"),
                            appList = appList
                        )
                    }
                }

                // Scope configs (skip missing packages)
                var removedCount = 0
                val installedPkgs = _allApps.value.map { it.packageName }.toSet()
                val newScope = linkedMapOf<String, AppScopeConfig>()
                if (json.has("scope")) {
                    val sObj = json.getJSONObject("scope")
                    sObj.keys().forEach { pkg ->
                        if (pkg !in installedPkgs) {
                            removedCount++
                            return@forEach
                        }
                        val s = sObj.getJSONObject(pkg)
                        val templates = mutableListOf<String>()
                        if (s.has("applyTemplates")) {
                            val arr = s.getJSONArray("applyTemplates")
                            for (i in 0 until arr.length()) {
                                val name = arr.getString(i)
                                // Cross-locale: prefixed built-in names from
                                // another language remap to this locale's form,
                                // so imported scope templates keep working.
                                val presetId = PresetNaming.resolveToId(app, name)
                                templates += if (presetId != null) {
                                    PresetNaming.toPrefixedName(app, presetId, name.endsWith("_whitelist"))
                                } else {
                                    name
                                }
                            }
                        }
                        val extra = mutableListOf<String>()
                        if (s.has("extraAppList")) {
                            val arr = s.getJSONArray("extraAppList")
                            for (i in 0 until arr.length()) extra.add(arr.getString(i))
                        }
                        newScope[pkg] = AppScopeConfig(
                            useWhitelist = s.optBoolean("useWhitelist", false),
                            aggressiveFilter = s.optBoolean("aggressiveFilter", false),
                            excludeSystemApps = s.optBoolean("excludeSystemApps", true),
                            enabledTemplates = templates,
                            extraAppList = extra
                        )
                    }
                }

                // Atomic replace + single persist per collection, then a
                // single silent HMA push (if takeover is active).
                _templates.value = newTemplates
                _scopeConfigs.value = newScope
                persistTemplates()
                persistScopeConfigs()
                pushToHmaNow()

                onScopeRemoved(removedCount)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { onDone() }
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
