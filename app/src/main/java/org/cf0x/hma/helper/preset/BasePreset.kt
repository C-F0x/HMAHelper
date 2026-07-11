package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo
import java.util.zip.ZipFile

abstract class BasePreset(val name: String) {
    internal val packageNames = mutableSetOf<String>()
    internal open val keywords: Set<String> = emptySet()

    protected abstract fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean

    val packages: Set<String> get() = packageNames.toSet()

    fun clearPackageList() = packageNames.clear()

    fun addPackageInfoPreset(appInfo: ApplicationInfo): Boolean {
        val packageName = appInfo.packageName
        if (packageNames.contains(packageName)) return false
        if (canBeAddedIntoPreset(appInfo) || keywords.any { packageName.contains(it) }) {
            packageNames.add(packageName)
            return true
        }
        return false
    }

    protected fun findAppsFromLibs(zipFile: ZipFile, libNames: Array<String>): Boolean {
        val architectures = arrayOf("arm64-v8a", "armeabi-v7a")
        for (entry in libNames) {
            for (arch in architectures) {
                if (zipFile.getEntry("lib/$arch/$entry") != null) {
                    return true
                }
            }
        }
        return false
    }

    override fun toString() = "${javaClass.simpleName} {" +
            " \"packageNames\": $packageNames }"
}
