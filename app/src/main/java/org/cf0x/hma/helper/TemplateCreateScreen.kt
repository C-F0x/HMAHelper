package org.cf0x.hma.helper

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.cf0x.hma.helper.ui.components.SegmentSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateCreateScreen(
    selectedApps: List<String> = emptyList(),
    editTemplateName: String? = null,
    onBackClick: () -> Unit,
    onSelectAppsClick: () -> Unit,
    viewModel: AppManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val templates by viewModel.templates.collectAsState()

    val reservedNames = remember(templates) {
        if (editTemplateName != null) viewModel.getReservedNames() - editTemplateName
        else viewModel.getReservedNames()
    }

    var templateName by remember { mutableStateOf("") }
    var isWhitelist by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<String>()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Populate from existing template once data is loaded
    var templatePopulated by remember { mutableStateOf(false) }
    LaunchedEffect(templates, editTemplateName) {
        if (!templatePopulated) {
            val existing = editTemplateName?.let { templates.find { t -> t.name == it } }
            if (existing != null) {
                templateName = existing.name
                isWhitelist = existing.isWhitelist
                apps = existing.appList
            }
            templatePopulated = true
        }
    }

    // Apply selectedApps from navigation when returning from app picker
    LaunchedEffect(selectedApps) {
        if (selectedApps.isNotEmpty()) {
            apps = selectedApps
        }
    }

    fun validateName(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return context.getString(R.string.template_name_empty)
        val lower = trimmed.lowercase()
        if (reservedNames.any { it.lowercase() == lower }) return context.getString(R.string.template_name_duplicate)
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_template_create), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val err = validateName(templateName)
                        if (err != null) {
                            nameError = err
                            return@IconButton
                        }
                        val t = Template(
                            name = templateName.trim(),
                            isWhitelist = isWhitelist,
                            appList = apps
                        )
                        if (editTemplateName != null) {
                            viewModel.updateTemplate(editTemplateName, t)
                            Toast.makeText(context, context.getString(R.string.template_updated), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addTemplate(t)
                            Toast.makeText(context, context.getString(R.string.template_created), Toast.LENGTH_SHORT).show()
                        }
                        onBackClick()
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.desc_save))
                    }
                    if (editTemplateName != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.desc_delete))
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Template name
            OutlinedTextField(
                value = templateName,
                onValueChange = {
                    templateName = it
                    nameError = validateName(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.template_name)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            )

            // Blacklist / Whitelist mode
            SegmentSwitch(
                options = listOf(
                    stringResource(R.string.config_blacklist),
                    stringResource(R.string.config_whitelist)
                ),
                selectedIndex = if (isWhitelist) 1 else 0,
                onSelect = { isWhitelist = it == 1 }
            )

            // Selected apps
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
                        Text(stringResource(R.string.template_app_list), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = onSelectAppsClick) { Text(stringResource(R.string.config_select_extra)) }
                    }
                    if (apps.isEmpty()) {
                        Text(
                            stringResource(R.string.template_no_apps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        apps.forEach { pkg ->
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text(stringResource(R.string.template_delete_title)) },
            text = { Text(stringResource(R.string.template_delete_message, editTemplateName ?: "")) },
            confirmButton = {
                Button(onClick = {
                    if (editTemplateName != null) viewModel.removeTemplate(editTemplateName)
                    showDeleteDialog = false
                    onBackClick()
                }) { Text(stringResource(R.string.template_delete_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
