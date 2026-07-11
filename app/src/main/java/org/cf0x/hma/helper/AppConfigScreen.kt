package org.cf0x.hma.helper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cf0x.hma.helper.preset.PresetManager
import org.cf0x.hma.helper.ui.components.SegmentSwitch

// ─────────────────────────────────────────────────
//  App Config Screen — batch scope settings
// ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigScreen(
    packageNames: List<String>,
    onBackClick: () -> Unit,
    onExtraAppListClick: (String, List<String>) -> Unit = { _, _ -> },
    viewModel: AppManagerViewModel = viewModel()
) {
    val scopeConfigs by viewModel.scopeConfigs.collectAsState()

    // Load existing config for single-select, defaults for multi
    val isMultiEdit = packageNames.size > 1
    val refPkg = packageNames.firstOrNull() ?: ""

    // ── State ──
    var enableHide by remember { mutableStateOf(true) }
    var useWhitelist by remember { mutableStateOf(false) }
    var excludeSystem by remember { mutableStateOf(true) }
    var aggressiveFilter by remember { mutableStateOf(false) }
    var templatesExpanded by remember { mutableStateOf(false) }
    var enabledTemplates by remember { mutableStateOf<Set<String>>(emptySet()) }
    var extraPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Load existing config on first composition (single-select only)
    val configLoaded = remember { mutableStateOf(false) }
    LaunchedEffect(refPkg) {
        if (!isMultiEdit && refPkg.isNotBlank() && !configLoaded.value) {
            val existing = scopeConfigs[refPkg]
            if (existing != null) {
                enableHide = existing.useWhitelist || existing.aggressiveFilter ||
                        existing.excludeSystemApps != true ||
                        existing.enabledTemplates.isNotEmpty() ||
                        existing.extraAppList.isNotEmpty()
                useWhitelist = existing.useWhitelist
                excludeSystem = existing.excludeSystemApps
                aggressiveFilter = existing.aggressiveFilter
                enabledTemplates = existing.enabledTemplates.toSet()
                extraPackages = existing.extraAppList
            }
            configLoaded.value = true
        }
    }

    // Watch for extraPackages updates from shared ViewModel (e.g. after ExtraAppListScreen)
    val refConfig = scopeConfigs[refPkg]
    LaunchedEffect(refConfig) {
        if (refConfig != null && configLoaded.value) {
            extraPackages = refConfig.extraAppList
        }
    }

    // PresetManager template names
    val blacklistTemplates = remember {
        PresetManager.PRESET_NAMES.map { TemplateItem(it, isWhitelist = false) }
    }
    val whitelistTemplates = remember {
        listOf(
            TemplateItem("privileged_apps", isWhitelist = true)
        )
    }

    // Clear selected templates when switching work mode
    val prevUseWhitelist = remember { mutableStateOf(useWhitelist) }
    LaunchedEffect(useWhitelist) {
        if (useWhitelist != prevUseWhitelist.value) {
            enabledTemplates = emptySet()
            prevUseWhitelist.value = useWhitelist
        }
    }

    // Templates to display based on current mode
    val currentTemplates = if (useWhitelist) whitelistTemplates else blacklistTemplates

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.nav_app_config),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isMultiEdit) "Editing ${packageNames.size} apps"
                            else refPkg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Save"
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Enable Hide toggle ──
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.config_enable_hide),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = enableHide,
                            onCheckedChange = { enableHide = it }
                        )
                    }

                    AnimatedVisibility(
                        visible = enableHide,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 1. Work Mode
                            SettingLabel(stringResource(R.string.config_work_mode))
                            SegmentSwitch(
                                options = listOf(
                                    stringResource(R.string.config_blacklist),
                                    stringResource(R.string.config_whitelist)
                                ),
                                selectedIndex = if (useWhitelist) 1 else 0,
                                onSelect = { useWhitelist = it == 1 }
                            )

                            // 2. excludeSystemApps (whitelist only)
                            AnimatedVisibility(
                                visible = useWhitelist,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(R.string.config_exclude_system), style = MaterialTheme.typography.bodyLarge)
                                        Switch(checked = excludeSystem, onCheckedChange = { excludeSystem = it })
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 3. Aggressive Filter
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.config_aggressive_filter), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = aggressiveFilter, onCheckedChange = { aggressiveFilter = it })
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 4. Templates (mode-specific)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { templatesExpanded = !templatesExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.config_templates_header, enabledTemplates.size),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Icon(
                                        imageVector = if (templatesExpanded) Icons.Default.ExpandLess
                                        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = templatesExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        currentTemplates.forEach { template ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    enabledTemplates = if (template.name in enabledTemplates)
                                                        enabledTemplates - template.name
                                                    else enabledTemplates + template.name
                                                }.padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Checkbox(
                                                    checked = template.name in enabledTemplates,
                                                    onCheckedChange = {
                                                        enabledTemplates = if (it) enabledTemplates + template.name
                                                        else enabledTemplates - template.name
                                                    }
                                                )
                                                Column {
                                                    Text(template.displayLabel, style = MaterialTheme.typography.bodyLarge)
                                                    Text(
                                                        text = if (template.isWhitelist) "(${stringResource(R.string.config_whitelist)})"
                                                        else "(${stringResource(R.string.config_blacklist)})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            // 5. Extra App List
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.config_extra_apps), style = MaterialTheme.typography.bodyLarge)
                                    TextButton(onClick = { onExtraAppListClick(refPkg, extraPackages) }) {
                                        Text("Select")
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (extraPackages.isEmpty()) {
                                    Text(
                                        text = "No extra apps selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                } else {
                                    extraPackages.forEach { pkg ->
                                        Text(
                                            text = pkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Save confirmation dialog ──
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Save Config") },
            text = { Text("Apply this configuration to ${packageNames.size} app(s)?") },
            confirmButton = {
                Button(onClick = {
                    val config = AppScopeConfig(
                        useWhitelist = useWhitelist,
                        aggressiveFilter = aggressiveFilter,
                        excludeSystemApps = excludeSystem,
                        enabledTemplates = enabledTemplates.toList(),
                        extraAppList = extraPackages
                    )
                    packageNames.forEach { pkg ->
                        viewModel.saveConfig(pkg, config)
                    }
                    showSaveDialog = false
                    onBackClick()
                }) { Text(stringResource(R.string.card_add_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

private data class TemplateItem(
    val name: String,
    val isWhitelist: Boolean
) {
    val displayLabel: String
        get() = when (name) {
            "xposed" -> "Xposed Modules"
            "embedded_xposed" -> "Embedded Xposed"
            "managers" -> "Managers"
            "privileged_apps" -> "Privileged Apps"
            "custom_rom" -> "Custom ROM"
            "accessibility_apps" -> "Accessibility Services"
            else -> name
        }
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}
