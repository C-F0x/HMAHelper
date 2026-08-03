package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo

abstract class BasePreset(val name: String) {
    internal val packageNames = mutableSetOf<String>()
    internal open val keywords: Set<String> = emptySet()

    protected abstract fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean

    /** Root-based detection: evaluate a package from [RootAppInfo] facts only. */
    protected open fun canBeAddedViaRoot(pkg: String, info: RootAppInfo): Boolean =
        keywords.any { pkg.contains(it) }

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

    fun addPackageInfoRoot(pkg: String, info: RootAppInfo): Boolean {
        if (packageNames.contains(pkg)) return false
        if (canBeAddedViaRoot(pkg, info)) {
            packageNames.add(pkg)
            return true
        }
        return false
    }

    override fun toString() = "${javaClass.simpleName} {" +
            " \"packageNames\": $packageNames }"
}
