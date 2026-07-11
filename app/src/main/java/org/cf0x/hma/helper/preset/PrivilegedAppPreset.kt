package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo

class PrivilegedAppPreset : BasePreset(NAME) {
    companion object {
        const val NAME = "privileged_apps"
    }

    override val keywords
        get() = PresetListLoader.privilegedApps

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean = false
}
