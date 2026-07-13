package org.cf0x.hma.helper

import android.content.Context
import org.cf0x.hma.helper.R

object PresetNaming {
    const val PREFIX = "HMAH_"
    private const val WHITELIST_SUFFIX = "_whitelist"

    data class PresetInfo(val id: String, val labelRes: Int)

    // All 6 presets in order
    val PRESETS = listOf(
        PresetInfo("xposed", R.string.preset_name_xposed),
        PresetInfo("embedded_xposed", R.string.preset_name_embedded_xposed),
        PresetInfo("managers", R.string.preset_name_managers),
        PresetInfo("privileged_apps", R.string.preset_name_privileged_apps),
        PresetInfo("custom_rom", R.string.preset_name_custom_rom),
        PresetInfo("accessibility_apps", R.string.preset_name_accessibility_apps),
    )

    /** Build prefixed name for a preset in the current locale */
    fun toPrefixedName(context: Context, presetId: String, isWhitelist: Boolean): String {
        val label = getLocalizedLabel(context, presetId)
        return if (isWhitelist) "${PREFIX}${label}_whitelist" else "${PREFIX}${label}"
    }

    /** Map of prefixedName → presetId for all 12 presets in the current locale */
    fun allPrefixedNames(context: Context): Map<String, String> {
        return PRESETS.flatMap { p ->
            val label = getLocalizedLabel(context, p.id)
            listOf(
                "${PREFIX}${label}" to p.id,
                "${PREFIX}${label}_whitelist" to p.id
            )
        }.toMap()
    }

    /** Resolve a prefixed name to internal preset ID, checking all supported locales */
    fun resolveToId(context: Context, name: String): String? {
        if (!name.startsWith(PREFIX)) return null
        val body = name.removePrefix(PREFIX).removeSuffix(WHITELIST_SUFFIX)
        if (body.isBlank()) return null

        for (preset in PRESETS) {
            for (localeLabel in getLabelsForAllLocales(context, preset.labelRes)) {
                if (body == localeLabel) return preset.id
            }
        }
        return null
    }

    fun isPrefixedName(name: String): Boolean = name.startsWith(PREFIX)

    private fun getLocalizedLabel(context: Context, presetId: String): String {
        val res = PRESETS.find { it.id == presetId }?.labelRes ?: return presetId
        return context.getString(res)
    }

    private fun getLabelsForAllLocales(context: Context, labelRes: Int): Set<String> {
        val labels = mutableSetOf<String>()
        val locales = listOf(
            java.util.Locale.ENGLISH,
            java.util.Locale.CHINESE,
            java.util.Locale.TAIWAN,
            java.util.Locale.JAPAN,
            java.util.Locale.KOREA,
            java.util.Locale.FRANCE
        )
        for (locale in locales) {
            try {
                val config = android.content.res.Configuration(context.resources.configuration)
                config.setLocale(locale)
                val localizedResources = context.createConfigurationContext(config).resources
                labels.add(localizedResources.getString(labelRes))
            } catch (_: Exception) { }
        }
        return labels
    }
}
