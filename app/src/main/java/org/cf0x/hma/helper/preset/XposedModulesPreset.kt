package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo
import android.util.Log

class XposedModulesPreset : BasePreset(NAME) {
    companion object {
        const val NAME = "xposed"
        private const val TAG = "XposedPreset"
    }

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean {
        val pkgName = appInfo.packageName
        val meta = appInfo.metaData ?: return false

        if (meta.containsKey("xposedminversion")) {
            Log.i(TAG, "MATCH (meta:xposedminversion) $pkgName")
            return true
        }
        if (meta.containsKey("xposeddescription")) {
            Log.i(TAG, "MATCH (meta:xposeddescription) $pkgName")
            return true
        }
        return false
    }
}
