package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo
import android.util.Log

class AccessibilityAppsPreset(private val presetManager: PresetManager) : BasePreset(NAME) {
    companion object {
        const val NAME = "accessibility_apps"
        private const val TAG = "AccessibilityPreset"
        const val PERM_ACCESSIBILITY = "\u0000a\u0000n\u0000d\u0000r\u0000o\u0000i\u0000d\u0000.\u0000p\u0000e\u0000r\u0000m\u0000i\u0000s\u0000s\u0000i\u0000o\u0000n\u0000.\u0000B\u0000I\u0000N\u0000D\u0000_\u0000A\u0000C\u0000C\u0000E\u0000S\u0000S\u0000I\u0000B\u0000I\u0000L\u0000I\u0000T\u0000Y\u0000_\u0000S\u0000E\u0000R\u0000V\u0000I\u0000C\u0000E"
    }

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean {
        val pkgName = appInfo.packageName
        val result = PresetUtils.checkSplitPackages(appInfo) { key, zipFile ->
            val manifestStr = presetManager.readManifest(key, zipFile)
            manifestStr.contains(PERM_ACCESSIBILITY)
        }
        if (result) Log.i(TAG, "MATCH $pkgName")
        return result
    }
}
