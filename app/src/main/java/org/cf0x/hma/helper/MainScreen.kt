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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cf0x.hma.helper.ui.theme.HMAHelperTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSettingsClick: () -> Unit,
    onAppManagerClick: () -> Unit,
    onPresetClick: (String) -> Unit,
    viewModel: PresetViewModel = viewModel()
) {
    val presetCounts by viewModel.presetCounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val totalApps = presetCounts.values.sum()

    // Dialog states
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Smart classification expand state
    var smartExpanded by remember { mutableStateOf(false) }

    // Track status block height for proportional sizing
    var statusHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val statusHeightDp: Dp = with(density) { statusHeightPx.toDp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HMA Helper",
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
                            contentDescription = "Settings"
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

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Status Block ──
            StatusBlock(
                totalApps = totalApps,
                scopeCount = 0,
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

            // ── App Manager Card ──
            if (statusHeightDp > 0.dp) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusHeightDp)
                        .clickable { onAppManagerClick() },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
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
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
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
                            .clickable { smartExpanded = !smartExpanded }
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
                            contentDescription = if (smartExpanded) "Collapse" else "Expand",
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
                Text(stringResource(R.string.dialog_message_import))
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
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
            confirmButton = {},
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
    totalApps: Int,
    scopeCount: Int,
    onSizeChanged: (Float) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onSizeChanged(it.height.toFloat()) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.main_total_apps, totalApps),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.main_scope_count, scopeCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
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
        modifier = modifier.clickable(onClick = onClick),
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
            .clickable { onClick() },
        shape = MaterialTheme.shapes.large,
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    HMAHelperTheme {
        MainScreen(onSettingsClick = {}, onAppManagerClick = {}, onPresetClick = {})
    }
}
