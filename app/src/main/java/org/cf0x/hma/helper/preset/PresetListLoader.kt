package org.cf0x.hma.helper.preset

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object PresetListLoader {

    val managers by lazy { read("managers.list") }
    val privilegedApps by lazy { read("privileged_apps.list") }
    val customRomKeywords by lazy { read("custom_rom.list") }
    val customRomPathFragments by lazy { read("custom_rom_path.list") }

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun read(filename: String): Set<String> {
        if (!::appContext.isInitialized) return emptySet()
        return runCatching {
            val input = appContext.assets.open("preset_lists/$filename")
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
                    .toSet()
            }
        }.getOrDefault(emptySet())
    }
}
