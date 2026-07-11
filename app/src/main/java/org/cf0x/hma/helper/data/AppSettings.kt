package org.cf0x.hma.helper.data

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.cf0x.hma.helper.R

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { MONET, PRESET }

enum class AppLocale(val tag: String, val labelRes: Int) {
    SYSTEM("", R.string.setting_language_system),
    ZH_CN("zh-CN", R.string.setting_language_zh),
    ZH_TW("zh-TW", R.string.setting_language_zh_tw),
    JA("ja", R.string.setting_language_ja),
    KO("ko", R.string.setting_language_ko),
    FR("fr", R.string.setting_language_fr),
    EN_US("en-US", R.string.setting_language_en);

    fun toLocale(): java.util.Locale = when (this) {
        SYSTEM -> java.util.Locale.getDefault()
        ZH_CN  -> java.util.Locale.CHINA
        ZH_TW  -> java.util.Locale.TAIWAN
        JA     -> java.util.Locale.JAPAN
        KO     -> java.util.Locale.KOREA
        FR     -> java.util.Locale.FRANCE
        EN_US  -> java.util.Locale.US
    }
}

class AppSettings(private val context: Context) {

    private object Keys {
        val THEME_MODE       = stringPreferencesKey("theme_mode")
        val COLOR_SOURCE       = stringPreferencesKey("color_source")
        val PRESET_COLOR       = intPreferencesKey("preset_color")
        val APP_LOCALE         = stringPreferencesKey("app_locale")
        val THEME_EXPRESSIVE   = booleanPreferencesKey("theme_expressive")
        val PALETTE_STYLE      = stringPreferencesKey("palette_style")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { p ->
        runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
            .also { if (it == ThemeMode.SYSTEM && p[Keys.THEME_MODE] != null && p[Keys.THEME_MODE] != ThemeMode.SYSTEM.name) Log.w("AppSettings", "Unknown THEME_MODE: ${p[Keys.THEME_MODE]}") }
    }

    val colorSource: Flow<ColorSource> = context.dataStore.data.map { p ->
        runCatching { ColorSource.valueOf(p[Keys.COLOR_SOURCE] ?: "") }
            .getOrDefault(ColorSource.MONET)
            .also { if (it == ColorSource.MONET && p[Keys.COLOR_SOURCE] != null && p[Keys.COLOR_SOURCE] != ColorSource.MONET.name) Log.w("AppSettings", "Unknown COLOR_SOURCE: ${p[Keys.COLOR_SOURCE]}") }
    }

    val presetColor: Flow<Color> = context.dataStore.data.map { p ->
        Color(p[Keys.PRESET_COLOR] ?: 0xFF6750A4.toInt())
    }

    val appLocale: Flow<AppLocale> = context.dataStore.data.map { p ->
        val tag = p[Keys.APP_LOCALE] ?: ""
        AppLocale.entries.find { it.tag == tag } ?: detectSystemLocale()
    }

    private fun detectSystemLocale(): AppLocale {
        val locale = java.util.Locale.getDefault()
        return when (locale.language) {
            "zh" -> if (locale.country in listOf("TW", "HK", "MO")) AppLocale.ZH_TW
                    else AppLocale.ZH_CN
            "ja" -> AppLocale.JA
            "ko" -> AppLocale.KO
            "fr" -> AppLocale.FR
            else -> AppLocale.EN_US
        }
    }

    val paletteStyle: Flow<PaletteStyle> = context.dataStore.data.map { p ->
        runCatching { PaletteStyle.valueOf(p[Keys.PALETTE_STYLE] ?: "") }
            .getOrDefault(PaletteStyle.TonalSpot)
            .also { if (it == PaletteStyle.TonalSpot && p[Keys.PALETTE_STYLE] != null && p[Keys.PALETTE_STYLE] != PaletteStyle.TonalSpot.name) Log.w("AppSettings", "Unknown PALETTE_STYLE: ${p[Keys.PALETTE_STYLE]}") }
    }

    val themeExpressive: Flow<Boolean> = context.dataStore.data.map { it[Keys.THEME_EXPRESSIVE] ?: true }

    suspend fun saveThemeMode(m: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = m.name }
    suspend fun saveColorSource(s: ColorSource) = context.dataStore.edit { it[Keys.COLOR_SOURCE] = s.name }
    suspend fun savePresetColor(c: Int) = context.dataStore.edit { it[Keys.PRESET_COLOR] = c }
    suspend fun saveAppLocale(l: AppLocale) = context.dataStore.edit { it[Keys.APP_LOCALE] = l.tag }
    suspend fun saveThemeExpressive(v: Boolean) = context.dataStore.edit { it[Keys.THEME_EXPRESSIVE] = v }
    suspend fun savePaletteStyle(s: PaletteStyle) = context.dataStore.edit { it[Keys.PALETTE_STYLE] = s.name }
}
