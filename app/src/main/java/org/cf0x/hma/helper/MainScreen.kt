package org.cf0x.hma.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.cf0x.hma.helper.PresetNaming
import org.cf0x.hma.helper.data.AppSettings
import org.cf0x.hma.helper.ui.theme.HMAHelperTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    appSettings: AppSettings,
    onSettingsClick: () -> Unit,
    onScopeSettingsClick: () -> Unit,
    onTemplateSettingsClick: () -> Unit,
    onPresetClick: (String) -> Unit,
    viewModel: PresetViewModel = viewModel(),
    appManagerVM: AppManagerViewModel = viewModel()
) {
    val presetCounts by viewModel.presetCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scopeCount by viewModel.scopeCount.collectAsState()
    val customTemplateCount by appManagerVM.templates.collectAsState()

    // Dialog states
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportPreviewDialog by remember { mutableStateOf(false) }
    var importPreviewTemplates by remember { mutableIntStateOf(0) }
    var importPreviewScope by remember { mutableIntStateOf(0) }
    var importPreviewJson by remember { mutableStateOf<String?>(null) }

    // Smart classification expand state
    var smartExpanded by remember { mutableStateOf(false) }
    // App manager expand state
    var appManagerExpanded by remember { mutableStateOf(false) }

    // Track status block height for proportional sizing
    var statusHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val statusHeightDp: Dp = with(density) { statusHeightPx.toDp() }

    // Misc expanded state
    var miscExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Misc config values
    val configVersion by appSettings.configVersion.collectAsState(initial = 93)
    val detailLog by appSettings.detailLog.collectAsState(initial = false)
    val maxLogSize by appSettings.maxLogSize.collectAsState(initial = 512)
    val forceMountData by appSettings.forceMountData.collectAsState(initial = true)
    val aggressiveFilter by appSettings.aggressiveFilter.collectAsState(initial = false)

    val context = androidx.compose.ui.platform.LocalContext.current

    fun buildExportJson(): JSONObject {
        val json = JSONObject()
        json.put("configVersion", configVersion)
        json.put("detailLog", detailLog)
        json.put("maxLogSize", maxLogSize)
        json.put("forceMountData", forceMountData)
        json.put("aggressiveFilter", aggressiveFilter)

        val templatesJson = JSONObject()
        // Export 12 preset templates (6 blacklist + 6 whitelist)
        PresetNaming.PRESETS.forEach { preset ->
            val blacklistPrefixed = PresetNaming.toPrefixedName(context, preset.id, false)
            val whitelistPrefixed = PresetNaming.toPrefixedName(context, preset.id, true)

            val detected = viewModel.presetAppList.value[preset.id]?.map { it.packageName } ?: emptyList()
            val detectedArr = JSONArray()
            detected.forEach { detectedArr.put(it) }

            val blObj = JSONObject()
            blObj.put("isWhitelist", false)
            blObj.put("appList", detectedArr)
            templatesJson.put(blacklistPrefixed, blObj)

            val wlObj = JSONObject()
            wlObj.put("isWhitelist", true)
            wlObj.put("appList", detectedArr)
            templatesJson.put(whitelistPrefixed, wlObj)
        }
        // Export custom templates
        appManagerVM.getTemplates().forEach { t ->
            val tObj = JSONObject()
            tObj.put("isWhitelist", t.isWhitelist)
            val appList = JSONArray()
            t.appList.forEach { appList.put(it) }
            tObj.put("appList", appList)
            templatesJson.put(t.name, tObj)
        }
        json.put("templates", templatesJson)

        val scopeJson = JSONObject()
        appManagerVM.scopeConfigs.value.forEach { (pkg, cfg) ->
            val sObj = JSONObject()
            sObj.put("aggressiveFilter", cfg.aggressiveFilter)
            sObj.put("useWhitelist", cfg.useWhitelist)
            sObj.put("excludeSystemApps", cfg.excludeSystemApps)
            val applyTemplates = JSONArray()
            cfg.enabledTemplates.forEach { name ->
                val preset = PresetNaming.PRESETS.find { it.id == name }
                applyTemplates.put(if (preset != null) PresetNaming.toPrefixedName(context, preset.id, cfg.useWhitelist) else name)
            }
            sObj.put("applyTemplates", applyTemplates)
            val extra = JSONArray()
            cfg.extraAppList.forEach { extra.put(it) }
            sObj.put("extraAppList", extra)
            scopeJson.put(pkg, sObj)
        }
        json.put("scope", scopeJson)
        return json
    }

    // ── Export launcher ──
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val json = buildExportJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toString(2).toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Import launcher ──
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return@launch
                val json = JSONObject(jsonStr)

                val templatesCount = if (json.has("templates")) json.getJSONObject("templates").length() else 0
                val scopeCount = if (json.has("scope")) json.getJSONObject("scope").length() else 0
                val hasMisc = json.has("configVersion") || json.has("detailLog") || json.has("maxLogSize") || json.has("forceMountData") || json.has("aggressiveFilter")
                val valid = json.has("templates") && json.has("scope")

                scope.launch(Dispatchers.Main) {
                    importPreviewTemplates = templatesCount
                    importPreviewScope = scopeCount
                    importPreviewJson = if (valid) jsonStr else null
                    showImportPreviewDialog = true
                }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) {
                    importPreviewTemplates = 0
                    importPreviewScope = 0
                    importPreviewJson = null
                    showImportPreviewDialog = true
                }
            }
        }
    }

    fun doImport(jsonStr: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(jsonStr)

                // 1. Clear all existing
                appManagerVM.getTemplates().toList().forEach { t ->
                    appManagerVM.removeTemplate(t.name)
                }
                appManagerVM.scopeConfigs.value.keys.toList().forEach { pkg ->
                    appManagerVM.removeConfig(pkg)
                }

                // 2. Misc config with defaults
                appSettings.saveConfigVersion(json.optInt("configVersion", 93))
                appSettings.saveDetailLog(json.optBoolean("detailLog", false))
                appSettings.saveMaxLogSize(json.optInt("maxLogSize", 512))
                appSettings.saveForceMountData(json.optBoolean("forceMountData", true))
                appSettings.saveAggressiveFilter(json.optBoolean("aggressiveFilter", false))

                // 3. Templates (skip built-in presets, keep custom ones)
                if (json.has("templates")) {
                    val tObj = json.getJSONObject("templates")
                    tObj.keys().forEach { name ->
                        // Skip prefixed preset names — they are built-in
                        if (PresetNaming.resolveToId(context, name) != null) return@forEach
                        val t = tObj.getJSONObject(name)
                        val appList = mutableListOf<String>()
                        val arr = t.getJSONArray("appList")
                        for (i in 0 until arr.length()) appList.add(arr.getString(i))
                        appManagerVM.addTemplate(org.cf0x.hma.helper.Template(
                            name = name,
                            isWhitelist = t.getBoolean("isWhitelist"),
                            appList = appList
                        ))
                    }
                }

                // 4. Scope configs (skip missing packages)
                var removedCount = 0
                val installedPkgs = appManagerVM.allApps.value.map { it.packageName }.toSet()
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
                            for (i in 0 until arr.length()) templates.add(arr.getString(i))
                        }
                        val extra = mutableListOf<String>()
                        if (s.has("extraAppList")) {
                            val arr = s.getJSONArray("extraAppList")
                            for (i in 0 until arr.length()) extra.add(arr.getString(i))
                        }
                        appManagerVM.saveConfig(pkg, org.cf0x.hma.helper.AppScopeConfig(
                            useWhitelist = s.optBoolean("useWhitelist", false),
                            aggressiveFilter = s.optBoolean("aggressiveFilter", false),
                            excludeSystemApps = s.optBoolean("excludeSystemApps", true),
                            enabledTemplates = templates,
                            extraAppList = extra
                        ))
                    }
                }

                val finalRemoved = removedCount
                scope.launch(Dispatchers.Main) {
                    viewModel.reload()
                    if (finalRemoved > 0) {
                        Toast.makeText(context, context.getString(R.string.dialog_import_removed_scope, finalRemoved), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.reload() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.preset_refresh)
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Status Block ──
            StatusBlock(
                scopeCount = scopeCount,
                customTemplateCount = customTemplateCount.size,
                isLoading = isLoading,
                onSizeChanged = { statusHeightPx = it }
            )

            // ── Import / Export Row ──
            if (statusHeightDp > 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        icon = Icons.Outlined.CloudDownload,
                        label = stringResource(R.string.main_import),
                        modifier = Modifier
                            .weight(1f)
                            .height(statusHeightDp),
                        onClick = { showImportDialog = true }
                    )
                    ActionCard(
                        icon = Icons.Outlined.CloudUpload,
                        label = stringResource(R.string.main_export),
                        modifier = Modifier
                            .weight(1f)
                            .height(statusHeightDp),
                        onClick = { showExportDialog = true }
                    )
                }
            }

            // ── App Manager (expandable) ──
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row — clickable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (statusHeightDp > 0.dp) statusHeightDp
                                else 72.dp
                            )
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { appManagerExpanded = !appManagerExpanded }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.main_app_manager),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (appManagerExpanded) Icons.Default.ExpandLess
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (appManagerExpanded) stringResource(R.string.desc_collapse)
                            else stringResource(R.string.desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expandable content
                    AnimatedVisibility(
                        visible = appManagerExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsEntry(
                                label = stringResource(R.string.main_scope_settings),
                                onClick = onScopeSettingsClick
                            )
                            SettingsEntry(
                                label = stringResource(R.string.main_template_settings),
                                onClick = onTemplateSettingsClick
                            )
                        }
                    }
                }
            }

            // ── Smart Classification Block ──
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row — clickable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (statusHeightDp > 0.dp) statusHeightDp
                                else 72.dp
                            )
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { smartExpanded = !smartExpanded }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.main_smart_classify),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (smartExpanded) Icons.Default.ExpandLess
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (smartExpanded) stringResource(R.string.desc_collapse)
                            else stringResource(R.string.desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expandable content: 6 preset cards
                    AnimatedVisibility(
                        visible = smartExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            viewModel.presetNames.chunked(2).forEach { rowNames ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowNames.forEach { name ->
                                        PresetBlock(
                                            label = stringResource(viewModel.getDisplayLabelRes(name)),
                                            subtitle = stringResource(viewModel.getSubtitleRes(name)),
                                            count = presetCounts[name] ?: 0,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onPresetClick(name) }
                                        )
                                    }
                                    if (rowNames.size < 2) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Misc Block (expandable) ──
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (statusHeightDp > 0.dp) statusHeightDp else 72.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { miscExpanded = !miscExpanded }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.misc_config),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (miscExpanded) Icons.Default.ExpandLess
                            else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (miscExpanded) stringResource(R.string.desc_collapse)
                            else stringResource(R.string.desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expandable content
                    AnimatedVisibility(
                        visible = miscExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. configVersion
                            OutlinedTextField(
                                value = configVersion.toString(),
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    val v = filtered.toIntOrNull()
                                    if (v != null && filtered.isNotEmpty()) {
                                        scope.launch { appSettings.saveConfigVersion(v) }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.misc_config_version)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                            )

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 2. detailLog
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.misc_detail_log), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = detailLog, onCheckedChange = {
                                    scope.launch { appSettings.saveDetailLog(it) }
                                })
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 3. maxLogSize
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.misc_max_log_size), style = MaterialTheme.typography.bodyLarge)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(256, 512, 1024).forEach { size ->
                                        FilterChip(
                                            selected = maxLogSize == size,
                                            onClick = { scope.launch { appSettings.saveMaxLogSize(size) } },
                                            label = { Text(stringResource(R.string.misc_max_log_size_k, size)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 4. forceMountData
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.misc_force_mount_data), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = forceMountData, onCheckedChange = {
                                    scope.launch { appSettings.saveForceMountData(it) }
                                })
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 5. aggressiveFilter (disabled)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.misc_aggressive_filter),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                    Switch(
                                        checked = aggressiveFilter,
                                        onCheckedChange = { },
                                        enabled = false
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.misc_aggressive_filter_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Import Dialog ──
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(stringResource(R.string.dialog_title_import))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.dialog_message_import))
                    Text(
                        stringResource(R.string.dialog_import_overwrite_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showImportDialog = false
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }) {
                    Text(stringResource(R.string.card_add_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // ── Import Preview Dialog ──
    if (showImportPreviewDialog) {
        val valid = importPreviewJson != null
        AlertDialog(
            onDismissRequest = { showImportPreviewDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(stringResource(R.string.dialog_import_preview_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (valid) {
                        Text(stringResource(R.string.dialog_import_preview_templates, importPreviewTemplates))
                        Text(stringResource(R.string.dialog_import_preview_scope, importPreviewScope))
                    } else {
                        Text(
                            stringResource(R.string.dialog_import_preview_corrupt),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportPreviewDialog = false
                        importPreviewJson?.let { doImport(it) }
                    },
                    enabled = valid
                ) {
                    Text(stringResource(R.string.dialog_import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportPreviewDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // ── Export Dialog ──
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text(stringResource(R.string.dialog_title_export))
            },
            text = {
                Text(stringResource(R.string.dialog_message_export))
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showExportDialog = false
                            exportLauncher.launch("HMA_Config.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dialog_export_saf))
                    }
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val json = buildExportJson()
                                    val content = json.toString(2)

                                    // Save to export directory
                                    val exportDir = java.io.File(context.filesDir, "HMA Helper/data/export")
                                    exportDir.mkdirs()
                                    val file = java.io.File(exportDir, "HMA_Config.json")
                                    file.writeText(content)

                                    scope.launch(Dispatchers.Main) {
                                        Toast.makeText(context, context.getString(R.string.dialog_export_quick_done), Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.dialog_export_quick))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────
//  Status Block  (simplified: no icon/title/subtitle)
// ─────────────────────────────────────────────────
@Composable
private fun StatusBlock(
    scopeCount: Int,
    customTemplateCount: Int,
    isLoading: Boolean = false,
    onSizeChanged: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onSizeChanged(it.height.toFloat()) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            // Row 1: Compat version
            Text(
                text = stringResource(R.string.version_compat_label, stringResource(R.string.version_compat)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Template apps
            Text(
                text = stringResource(R.string.main_total_apps, customTemplateCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            // Row 3: Scope apps
            Text(
                text = stringResource(R.string.main_scope_count, scopeCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────
//  Action Card (Import / Export)
// ─────────────────────────────────────────────────
@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.clip(MaterialTheme.shapes.extraLarge).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────
//  Preset Block (unchanged from original)
// ─────────────────────────────────────────────────
@Composable
private fun PresetBlock(
    label: String,
    subtitle: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 2
                )
            }
            Text(
                text = stringResource(R.string.preset_count_suffix, count),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Settings Entry ──
@Composable
private fun SettingsEntry(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).clickable { onClick() },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    HMAHelperTheme {
        MainScreen(appSettings = TODO(), onSettingsClick = {}, onScopeSettingsClick = {}, onTemplateSettingsClick = {}, onPresetClick = {})
    }
}
