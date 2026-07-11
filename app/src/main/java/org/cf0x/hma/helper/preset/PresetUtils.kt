package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo
import java.util.zip.ZipFile

object PresetUtils {

    fun checkSplitPackages(appInfo: ApplicationInfo, onZipFile: (String, ZipFile) -> Boolean): Boolean {
        val allLocations = listOf(appInfo.sourceDir, appInfo.publicSourceDir)
            .filterNotNull()
            .distinct()

        return allLocations.any { filePath ->
            runCatching {
                ZipFile(filePath).use { zipFile ->
                    if (onZipFile(filePath, zipFile)) {
                        return@runCatching true
                    }
                }
                false
            }.getOrDefault(false)
        }
    }
}
