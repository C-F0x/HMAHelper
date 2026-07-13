package org.cf0x.hma.helper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeSettingsScreen(
    onBackClick: () -> Unit,
    onCreateConfigClick: () -> Unit,
    onConfigClick: (String) -> Unit,
    viewModel: AppManagerViewModel = viewModel()
) {
    val scopeConfigs by viewModel.scopeConfigs.collectAsState()
    val allAppsList by viewModel.allApps.collectAsState()
    val appLabelMap = remember(allAppsList) {
        allAppsList.associate { it.packageName to it.appLabel }
    }

    val blacklistConfigs = remember(scopeConfigs) {
        scopeConfigs.filter { !it.value.useWhitelist }.entries.sortedBy { it.key }
    }
    val whitelistConfigs = remember(scopeConfigs) {
        scopeConfigs.filter { it.value.useWhitelist }.entries.sortedBy { it.key }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.main_scope_settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = onCreateConfigClick) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.desc_add))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (scopeConfigs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.scope_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // ── Blacklist section ──
                if (blacklistConfigs.isNotEmpty()) {
                    item(key = "blacklist_header") {
                        ModeGroupCard(
                            modeLabel = stringResource(R.string.scope_mode_blacklist),
                            labelColor = MaterialTheme.colorScheme.error,
                            bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        ) {
                            blacklistConfigs.forEach { (pkg, config) ->
                                ConfigEntryRow(
                                    pkg = pkg,
                                    label = appLabelMap[pkg] ?: pkg,
                                    templateCount = config.enabledTemplates.size,
                                    extraCount = config.extraAppList.size,
                                    onClick = { onConfigClick(pkg) }
                                )
                            }
                        }
                    }
                }

                // ── Whitelist section ──
                if (whitelistConfigs.isNotEmpty()) {
                    item(key = "whitelist_header") {
                        ModeGroupCard(
                            modeLabel = stringResource(R.string.scope_mode_whitelist),
                            labelColor = MaterialTheme.colorScheme.primary,
                            bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ) {
                            whitelistConfigs.forEach { (pkg, config) ->
                                ConfigEntryRow(
                                    pkg = pkg,
                                    label = appLabelMap[pkg] ?: pkg,
                                    templateCount = config.enabledTemplates.size,
                                    extraCount = config.extraAppList.size,
                                    onClick = { onConfigClick(pkg) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModeGroupCard(
    modeLabel: String,
    labelColor: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Mode label strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(bgColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = labelColor
                )
            }
            // Entries
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ConfigEntryRow(
    pkg: String,
    label: String,
    templateCount: Int,
    extraCount: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(pkg) {
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(pkg)
        }.getOrNull()
        iconBitmap = drawable?.toBitmap(48, 48, null)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon
            val icon = iconBitmap
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.scope_config_count, templateCount, extraCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
