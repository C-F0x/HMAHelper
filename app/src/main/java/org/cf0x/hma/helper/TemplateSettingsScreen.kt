package org.cf0x.hma.helper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateSettingsScreen(
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onTemplateClick: (String) -> Unit = {},
    viewModel: AppManagerViewModel = viewModel()
) {
    val templates by viewModel.templates.collectAsState()

    val blacklistTemplates = remember(templates) {
        templates.filter { !it.isWhitelist }.sortedBy { it.name.lowercase() }
    }
    val whitelistTemplates = remember(templates) {
        templates.filter { it.isWhitelist }.sortedBy { it.name.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.main_template_settings),
                        fontWeight = FontWeight.Bold
                    )
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
                    IconButton(onClick = onCreateClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.desc_create_template)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.template_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // ── Blacklist section ──
                if (blacklistTemplates.isNotEmpty()) {
                    item(key = "blacklist_header") {
                        ModeGroupCard(
                            modeLabel = stringResource(R.string.scope_mode_blacklist),
                            labelColor = MaterialTheme.colorScheme.error,
                            bgColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        ) {
                            blacklistTemplates.forEach { template ->
                                TemplateEntryRow(
                                    template = template,
                                    onClick = { onTemplateClick(template.name) }
                                )
                            }
                        }
                    }
                }

                // ── Whitelist section ──
                if (whitelistTemplates.isNotEmpty()) {
                    item(key = "whitelist_header") {
                        ModeGroupCard(
                            modeLabel = stringResource(R.string.scope_mode_whitelist),
                            labelColor = MaterialTheme.colorScheme.primary,
                            bgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ) {
                            whitelistTemplates.forEach { template ->
                                TemplateEntryRow(
                                    template = template,
                                    onClick = { onTemplateClick(template.name) }
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
private fun TemplateEntryRow(
    template: Template,
    onClick: () -> Unit
) {
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
            // Template icon placeholder
            Surface(
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (template.isWhitelist) stringResource(R.string.scope_mode_whitelist)
                    else stringResource(R.string.scope_mode_blacklist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.template_app_count, template.appList.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
