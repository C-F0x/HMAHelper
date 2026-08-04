package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo
import android.util.Log

class CustomROMPreset : BasePreset(NAME) {
    companion object {
        const val NAME = "custom_rom"
        private const val TAG = "CustomROMPreset"
    }

    override val keywords
        get() = PresetListLoader.customRomKeywords

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean {
        // APK path fragments from data file
        if (PresetListLoader.customRomPathFragments.any { appInfo.sourceDir.contains(it) }) {
            Log.v(TAG, "MATCH (path) $appInfo.packageName")
            return true
        }
        // keyword matching handled by BasePreset.addPackageInfoPreset
        return false
    }

    override fun canBeAddedViaRoot(pkg: String, info: RootAppInfo): Boolean {
        val path = info.apkPath
        if (path != null && PresetListLoader.customRomPathFragments.any { path.contains(it) }) {
            Log.v(TAG, "MATCH (root:path) $pkg")
            return true
        }
        return super.canBeAddedViaRoot(pkg, info)
    }
}
