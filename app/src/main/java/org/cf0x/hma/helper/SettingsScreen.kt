package org.cf0x.hma.helper

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.cf0x.hma.helper.data.AppLocale
import org.cf0x.hma.helper.data.AppSettings
import org.cf0x.hma.helper.data.ColorSource
import org.cf0x.hma.helper.data.ThemeMode
import org.cf0x.hma.helper.ui.components.ColorPickerWheel
import org.cf0x.hma.helper.ui.components.SegmentSwitch
import org.cf0x.hma.helper.util.applyLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appSettings: AppSettings,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val supportsMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var loaded by remember { mutableStateOf(false) }

    var themeMode    by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var colorSource  by remember { mutableStateOf(if (supportsMonet) ColorSource.MONET else ColorSource.PRESET) }
    var savedColor   by remember { mutableStateOf(Color(0xFF6750A4)) }
    val appLocale    by appSettings.appLocale.collectAsState(initial = AppLocale.SYSTEM)
    var paletteStyle by remember { mutableStateOf(PaletteStyle.TonalSpot) }

    LaunchedEffect(Unit) {
        themeMode    = appSettings.themeMode.first()
        colorSource  = appSettings.colorSource.first()
        savedColor   = appSettings.presetColor.first()
        paletteStyle = appSettings.paletteStyle.first()
        loaded       = true
    }

    var previewColor by remember { mutableStateOf(savedColor) }
    var showPicker   by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { showPicker = false }
    LaunchedEffect(colorSource) { if (colorSource != ColorSource.PRESET) showPicker = false }

    if (!loaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Group: About ---
            SettingGroup {
                AboutItem(context)
            }

            // --- Group: Theme Color & Style ---
            SettingGroup {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingHeader(icon = Icons.Outlined.Palette, title = stringResource(R.string.setting_color_source))
                    val colorOptions = if (supportsMonet)
                        listOf(stringResource(R.string.setting_color_system), stringResource(R.string.setting_color_custom))
                    else
                        listOf(stringResource(R.string.setting_color_custom))
                    val selectedIndex = if (supportsMonet && colorSource == ColorSource.MONET) 0 else 1

                    SegmentSwitch(
                        options       = colorOptions,
                        selectedIndex = selectedIndex,
                        onSelect      = { index ->
                            val next = if (supportsMonet && index == 0) ColorSource.MONET else ColorSource.PRESET
                            scope.launch { appSettings.saveColorSource(next) }
                            colorSource = next
                            if (next == ColorSource.PRESET) showPicker = !showPicker
                        }
                    )

                    AnimatedVisibility(visible = colorSource == ColorSource.PRESET && showPicker, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ColorPickerWheel(initialColor = previewColor, onColorChanged = { previewColor = it }, modifier = Modifier.padding(top = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { previewColor = savedColor; showPicker = false }) { Text(stringResource(R.string.card_add_cancel)) }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { scope.launch { appSettings.savePresetColor(previewColor.toArgb()) }; savedColor = previewColor; showPicker = false }) { Text(stringResource(R.string.card_add_confirm)) }
                            }
                        }
                    }

                    AnimatedVisibility(visible = colorSource == ColorSource.PRESET && !showPicker, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { showPicker = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(modifier = Modifier.size(28.dp), shape = MaterialTheme.shapes.small, color = savedColor, tonalElevation = 2.dp) {}
                                Text(text = "#%06X".format(savedColor.toArgb() and 0xFFFFFF), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Palette Style selector
                    PaletteStyleItem(paletteStyle) { paletteStyle = it; scope.launch { appSettings.savePaletteStyle(it) } }
                }
            }

            // --- Group: Theme Mode ---
            SettingGroup {
                SettingHeader(icon = Icons.Filled.DarkMode, title = stringResource(R.string.setting_theme_mode))
                SegmentSwitch(
                    options       = listOf(stringResource(R.string.setting_theme_system), stringResource(R.string.setting_theme_light), stringResource(R.string.setting_theme_dark)),
                    selectedIndex = themeMode.ordinal,
                    onSelect      = { scope.launch { appSettings.saveThemeMode(ThemeMode.entries[it]) }; themeMode = ThemeMode.entries[it] }
                )
            }

            // --- Group: Language ---
            SettingGroup {
                LanguageItem(appSettings, appLocale)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.extraLarge,
        colors   = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            content  = content
        )
    }
}

@Composable
private fun SettingHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun PaletteStyleItem(current: PaletteStyle, onSelect: (PaletteStyle) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf(
        PaletteStyle.TonalSpot  to stringResource(R.string.setting_palette_style_tonalspot),
        PaletteStyle.Neutral    to stringResource(R.string.setting_palette_style_neutral),
        PaletteStyle.Vibrant    to stringResource(R.string.setting_palette_style_vibrant),
        PaletteStyle.Expressive to stringResource(R.string.setting_palette_style_expressive),
        PaletteStyle.Monochrome to stringResource(R.string.setting_palette_style_monochrome),
        PaletteStyle.Fidelity   to stringResource(R.string.setting_palette_style_fidelity),
        PaletteStyle.Rainbow    to stringResource(R.string.setting_palette_style_rainbow),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Colorize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_palette_style), style = MaterialTheme.typography.bodyLarge)
                if (!expanded) {
                    Text(
                        options.firstOrNull { it.first == current }?.second ?: current.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                options.forEach { (style, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { expanded = false; onSelect(style) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = current == style, onClick = { expanded = false; onSelect(style) })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageItem(appSettings: AppSettings, appLocale: AppLocale) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pending  by remember(appLocale) { mutableStateOf(appLocale) }
    val options  = AppLocale.entries.filter { it != AppLocale.SYSTEM }.map {
        stringResource(it.labelRes) to it
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.setting_language),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!expanded) {
                    Text(
                        options.firstOrNull { it.second == appLocale }?.first ?: stringResource(R.string.setting_language_system),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                options.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { pending = value }.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = pending == value, onClick = { pending = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pending = appLocale; expanded = false }) { Text(stringResource(R.string.card_add_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        expanded = false
                        if (pending == appLocale) return@Button
                        kotlinx.coroutines.runBlocking { appSettings.saveAppLocale(pending) }
                        context.applyLocale(pending.tag)
                    }) { Text(stringResource(R.string.card_add_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun AboutItem(context: android.content.Context) {
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    val openGitHub: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.github_url)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Column(
        modifier = Modifier.clickable { openGitHub() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.github_author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

