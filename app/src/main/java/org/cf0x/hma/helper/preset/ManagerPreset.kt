package org.cf0x.hma.helper.preset

import android.content.pm.ApplicationInfo

class ManagerPreset : BasePreset(NAME) {
    companion object {
        const val NAME = "managers"
    }

    override val keywords
        get() = PresetListLoader.managers

    override fun canBeAddedIntoPreset(appInfo: ApplicationInfo): Boolean = false
}
