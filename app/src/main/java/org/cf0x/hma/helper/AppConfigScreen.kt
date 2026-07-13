package org.cf0x.hma.helper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
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
    var showDeleteDialog by remember { mutableStateOf(false) }

    val presetContext = LocalContext.current

    // Load existing config — wait for DataStore to supply scopeConfigs first
    LaunchedEffect(refPkg) {
        if (!isMultiEdit && refPkg.isNotBlank()) {
            snapshotFlow { scopeConfigs[refPkg] }
                .dropWhile { it == null && scopeConfigs.isEmpty() }
                .first()
                ?.let { existing ->
                    enableHide = existing.useWhitelist || existing.aggressiveFilter ||
                            existing.excludeSystemApps != true ||
                            existing.enabledTemplates.isNotEmpty() ||
                            existing.extraAppList.isNotEmpty()
                    useWhitelist = existing.useWhitelist
                    excludeSystem = existing.excludeSystemApps
                    aggressiveFilter = existing.aggressiveFilter
                    enabledTemplates = existing.enabledTemplates.map { name ->
                        val preset = PresetNaming.PRESETS.find { it.id == name }
                        if (preset != null) PresetNaming.toPrefixedName(presetContext, preset.id, existing.useWhitelist) else name
                    }.toSet()
                    extraPackages = existing.extraAppList
                }
        }
    }

    // Watch for extraPackages updates from shared ViewModel (e.g. after ExtraAppListScreen)
    val refConfig = scopeConfigs[refPkg]
    LaunchedEffect(refConfig) {
        if (refConfig != null) {
            extraPackages = refConfig.extraAppList
        }
    }

    // Preset templates (12: 6 blacklist + 6 whitelist, prefixed)
    val presetTemplates = remember(presetContext) {
        PresetNaming.PRESETS.flatMap { p ->
            val label = presetContext.getString(p.labelRes)
            listOf(
                TemplateItem("${PresetNaming.PREFIX}${label}", p.labelRes, R.string.scope_mode_blacklist),
                TemplateItem("${PresetNaming.PREFIX}${label}_whitelist", p.labelRes, R.string.scope_mode_whitelist)
            )
        }
    }
    // Custom templates from ViewModel
    val customTemplates by viewModel.templates.collectAsState()

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
                            text = if (isMultiEdit) stringResource(R.string.config_editing_count, packageNames.size)
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
                            contentDescription = stringResource(R.string.desc_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.desc_save)
                        )
                    }
                    if (!isMultiEdit) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.desc_delete)
                            )
                        }
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

                            // 4. Templates (smart classification + custom)
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { templatesExpanded = !templatesExpanded },
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
                                        // ── Smart Classification presets ──
                                        Text(
                                            text = stringResource(R.string.main_smart_classify),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                        presetTemplates.forEach { template ->
                                            TemplateCheckboxRow(
                                                name = template.storedName,
                                                label = { Text(stringResource(template.displayLabelRes), style = MaterialTheme.typography.bodyLarge) },
                                                subLabel = { Text(stringResource(template.modeLabelRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) },
                                                checked = template.storedName in enabledTemplates,
                                                onToggle = {
                                                    enabledTemplates = if (template.storedName in enabledTemplates)
                                                        enabledTemplates - template.storedName
                                                    else enabledTemplates + template.storedName
                                                }
                                            )
                                        }

                                        // ── Custom templates ──
                                        if (customTemplates.isNotEmpty()) {
                                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = stringResource(R.string.main_template_create),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                            )
                                            customTemplates.forEach { t ->
                                                TemplateCheckboxRow(
                                                    name = t.name,
                                                    label = { Text(t.name, style = MaterialTheme.typography.bodyLarge) },
                                                    subLabel = {
                                                        Text(
                                                            if (t.isWhitelist) stringResource(R.string.scope_mode_whitelist) else stringResource(R.string.scope_mode_blacklist),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    },
                                                    checked = t.name in enabledTemplates,
                                                    onToggle = {
                                                        enabledTemplates = if (t.name in enabledTemplates)
                                                            enabledTemplates - t.name
                                                        else enabledTemplates + t.name
                                                    }
                                                )
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
                                        Text(stringResource(R.string.config_select_extra))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (extraPackages.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.config_no_extra_apps),
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
            title = { Text(stringResource(R.string.config_save_title)) },
            text = { Text(stringResource(R.string.config_save_message, packageNames.size)) },
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
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.config_delete_title)) },
            text = { Text(stringResource(R.string.config_delete_message, packageNames.size)) },
            confirmButton = {
                Button(onClick = {
                    packageNames.forEach { pkg -> viewModel.removeConfig(pkg) }
                    showDeleteDialog = false
                    onBackClick()
                }) { Text(stringResource(R.string.config_delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

private data class TemplateItem(
    val storedName: String,
    val displayLabelRes: Int,
    val modeLabelRes: Int
)

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun TemplateCheckboxRow(
    name: String,
    label: @Composable () -> Unit,
    subLabel: @Composable () -> Unit,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onToggle() }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column {
            label()
            subLabel()
        }
    }
}