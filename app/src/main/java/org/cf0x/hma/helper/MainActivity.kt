package org.cf0x.hma.helper

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.materialkolor.PaletteStyle
import org.cf0x.hma.helper.data.AppLocale
import org.cf0x.hma.helper.data.AppSettings
import org.cf0x.hma.helper.data.ColorSource
import org.cf0x.hma.helper.data.ThemeMode
import org.cf0x.hma.helper.ui.theme.HMAHelperTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        appSettings = AppSettings(applicationContext)

        setContent {
            val themeMode       by appSettings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val colorSource     by appSettings.colorSource.collectAsState(initial = ColorSource.MONET)
            val presetColor     by appSettings.presetColor.collectAsState(initial = Color(0xFF6750A4))
            val paletteStyle    by appSettings.paletteStyle.collectAsState(initial = PaletteStyle.TonalSpot)

            HMAHelperTheme(
                themeMode    = themeMode,
                colorSource  = colorSource,
                seedColor    = presetColor,
                paletteStyle = paletteStyle,
            ) {
                AppNavigation(appSettings)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync with system per-app language (API 33+). If the user changed it via
        // system Settings → Apps → Language while we were in background, our
        // DataStore should follow so the UI reflects the correct selection.
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val lm = getSystemService(android.app.LocaleManager::class.java) ?: return@runCatching
                val sysTags = lm.applicationLocales.toLanguageTags()
                if (sysTags.isNotBlank()) {
                    val matched = AppLocale.entries.firstOrNull { sysTags.startsWith(it.tag) && it.tag.isNotBlank() }
                    if (matched != null) runBlocking { appSettings.saveAppLocale(matched) }
                } else {
                    runBlocking { appSettings.saveAppLocale(AppLocale.SYSTEM) }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(appSettings: AppSettings) {
    val navController = rememberNavController()
    val appManagerVM: AppManagerViewModel = viewModel()
    
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                appSettings = appSettings,
                onSettingsClick = {
                    navController.navigate("settings")
                },
                onScopeSettingsClick = {
                    navController.navigate("scope_settings")
                },
                onTemplateSettingsClick = {
                    navController.navigate("template_settings")
                },
                onPresetClick = { name ->
                    navController.navigate("preset/$name")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                appSettings = appSettings,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = "preset/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: return@composable
            PresetScreen(
                presetName = name,
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("app_manager") {
            AppManagerScreen(
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onAppConfigClick = { pkgs ->
                    appManagerVM.clearSelection()
                    val pkgsStr = pkgs.joinToString(",")
                    navController.navigate("app_config/$pkgsStr")
                }
            )
        }
        composable(
            route = "app_config/{packageNames}",
            arguments = listOf(navArgument("packageNames") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkgsStr = backStackEntry.arguments?.getString("packageNames") ?: return@composable
            val pkgs = pkgsStr.split(",").filter { it.isNotBlank() }
            AppConfigScreen(
                packageNames = pkgs,
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onExtraAppListClick = { configPkg, currentExtra ->
                    val existing = appManagerVM.getConfig(configPkg) ?: AppScopeConfig()
                    appManagerVM.saveConfig(configPkg, existing.copy(extraAppList = currentExtra))
                    navController.navigate("extra_app_list/$configPkg")
                }
            )
        }
        composable(
            route = "extra_app_list/{configPackageName}",
            arguments = listOf(navArgument("configPackageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val configPkg = backStackEntry.arguments?.getString("configPackageName") ?: return@composable
            val initialExtra = appManagerVM.getConfig(configPkg)?.extraAppList ?: emptyList()
            ExtraAppListScreen(
                configPackageName = configPkg,
                initialSelection = initialExtra,
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onConfirm = { selectedPkgs ->
                    val existing = appManagerVM.getConfig(configPkg) ?: AppScopeConfig()
                    appManagerVM.saveConfig(configPkg, existing.copy(extraAppList = selectedPkgs))
                    navController.popBackStack()
                }
            )
        }
        composable("scope_settings") {
            ScopeSettingsScreen(
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onCreateConfigClick = { navController.navigate("scope_select") },
                onConfigClick = { pkg -> navController.navigate("app_config/$pkg") }
            )
        }
        composable("scope_select") {
            AppManagerScreen(
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onExtraConfirm = { selected ->
                    val pkgsStr = selected.joinToString(",")
                    navController.navigate("app_config/$pkgsStr") {
                        popUpTo("scope_settings") { inclusive = false }
                    }
                }
            )
        }
        composable("template_settings") {
            TemplateSettingsScreen(
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onCreateClick = { navController.navigate("template_create") },
                onTemplateClick = { name ->
                    navController.navigate("template_edit/$name")
                }
            )
        }
        composable("template_create") { entry ->
            val appResult by entry.savedStateHandle.getStateFlow<List<String>>("template_apps", emptyList())
                .collectAsState()
            TemplateCreateScreen(
                viewModel = appManagerVM,
                selectedApps = appResult,
                onBackClick = { navController.popBackStack() },
                onSelectAppsClick = {
                    navController.navigate("template_app_select")
                }
            )
        }
        composable(
            route = "template_edit/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { backStackEntry ->
            val editName = backStackEntry.arguments?.getString("name") ?: return@composable
            val appResult by backStackEntry.savedStateHandle.getStateFlow<List<String>>("template_apps", emptyList())
                .collectAsState()
            TemplateCreateScreen(
                viewModel = appManagerVM,
                selectedApps = appResult,
                editTemplateName = editName,
                onBackClick = { navController.popBackStack() },
                onSelectAppsClick = {
                    navController.navigate("template_app_select")
                }
            )
        }
        composable("template_app_select") {
            AppManagerScreen(
                viewModel = appManagerVM,
                onBackClick = { navController.popBackStack() },
                onExtraConfirm = { selected ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("template_apps", selected)
                    navController.popBackStack()
                }
            )
        }
    }
}
