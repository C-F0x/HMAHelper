package org.cf0x.hma.helper

import android.app.Application
import android.os.Build
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.cf0x.hma.helper.data.AppLocale
import org.cf0x.hma.helper.data.AppSettings
import org.cf0x.hma.helper.util.applyLocale

class HMAApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val appSettings = AppSettings(this)

        // Sync with system per-app language (API 33+). If the user changed it via
        // system Settings → Apps → Language, our DataStore should follow.
        val systemTag = if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val lm = getSystemService(android.app.LocaleManager::class.java) ?: return@runCatching ""
                lm.applicationLocales.toLanguageTags() ?: ""
            }.getOrDefault("")
        } else ""

        val effectiveTag = if (systemTag.isNotBlank()) {
            val matched = AppLocale.entries.firstOrNull { systemTag.startsWith(it.tag) && it.tag.isNotBlank() }
            if (matched != null) runBlocking { appSettings.saveAppLocale(matched) }
            systemTag
        } else {
            runCatching {
                runBlocking { appSettings.appLocale.first().tag }
            }.getOrDefault(AppLocale.EN_US.tag)
        }

        applyLocale(effectiveTag)
    }
}
