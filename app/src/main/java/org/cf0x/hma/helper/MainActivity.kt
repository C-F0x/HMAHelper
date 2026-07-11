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
            val themeExpressive by appSettings.themeExpressive.collectAsState(initial = true)
            val paletteStyle    by appSettings.paletteStyle.collectAsState(initial = PaletteStyle.TonalSpot)

            HMAHelperTheme(
                themeMode    = themeMode,
                colorSource  = colorSource,
                seedColor    = presetColor,
                isExpressive = themeExpressive,
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
                }
            }
        }
    }
}

@Composable
fun AppNavigation(appSettings: AppSettings) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                onSettingsClick = {
                    navController.navigate("settings")
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
    }
}
