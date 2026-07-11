package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo
import android.util.Log

class EmbeddedXposedPreset : BasePreset(NAME) {
    companion object {
        const val NAME = "embedded_xposed"
        private const val TAG = "EmbeddedXposed"
    }

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean {
        val pkgName = appInfo.packageName

        val meta = appInfo.metaData
        if (meta != null) {
            for (key in arrayOf("lspatch", "jshook", "xposedmodule", "com.taichi", "va_xposed")) {
                if (meta.containsKey(key)) {
                    Log.i(TAG, "MATCH (meta:$key) $pkgName")
                    return true
                }
            }
        }

        val apkPath = appInfo.sourceDir
        if (apkPath.isNullOrEmpty()) return false

        return runCatching {
            java.util.zip.ZipFile(apkPath).use { zip ->
                if (zip.getEntry("assets/xposed_init") != null) {
                    Log.i(TAG, "MATCH (zip:xposed_init) $pkgName")
                    return@runCatching true
                }
                val libs = arrayOf("libsandhook.so", "libpine.so", "libxposed.so", "libxposed_lsp.so")
                val arches = arrayOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                for (lib in libs) {
                    for (arch in arches) {
                        if (zip.getEntry("lib/$arch/$lib") != null) {
                            Log.i(TAG, "MATCH (lib:$lib) $pkgName")
                            return@runCatching true
                        }
                    }
                }
                false
            }
        }.onFailure { e ->
            Log.v(TAG, "Zip error $pkgName: ${e.message}")
        }.getOrDefault(false)
    }
}
