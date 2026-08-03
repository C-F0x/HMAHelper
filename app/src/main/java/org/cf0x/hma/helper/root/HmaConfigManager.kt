package org.cf0x.hma.helper.root

import android.util.Log

/**
 * Direct root access to Hide My Applist's own configuration file.
 *
 * HMA stores its canonical config at
 *   /data/data/com.tsng.hidemyapplist/files/config.json
 * and does NOT watch the file for external changes — the app (re)loads it at
 * startup. After [writeConfig] the caller should prompt the user to restart
 * HMA; [restartHma] does that for them via root.
 */
object HmaConfigManager {

    private const val TAG = "HmaConfigManager"
    private const val HMA_PACKAGE = "com.tsng.hidemyapplist"
    private const val APP_CONFIG_PATH = "/data/data/com.tsng.hidemyapplist/files/config.json"

    data class HmaRestartResult(val stoppedPid: Int?, val startedPid: Int?)

    /** The app-side config path, or null if HMA is not installed / not accessible. */
    fun findAppConfigPath(): String? {
        val out = RootShell.exec("ls -d \"$APP_CONFIG_PATH\" 2>/dev/null")
        return if (out.isNullOrBlank()) null else APP_CONFIG_PATH
    }

    /** Read HMA's current config.json, or null on failure. */
    fun readConfig(): String? {
        val path = findAppConfigPath() ?: return null
        return RootShell.exec("cat \"$path\"", timeoutMs = 30000)
    }

    /**
     * Write the full HMA config JSON (the format produced by MainScreen's
     * buildExportJson). Returns true only when the written file verifies by
     * read-back.
     */
    fun writeConfig(json: String): Boolean {
        val path = findAppConfigPath() ?: run {
            Log.w(TAG, "HMA config not found at $APP_CONFIG_PATH")
            return false
        }

        // 1. Write content via su stdin.
        val written = RootShell.exec("cat > \"$path\"", input = json)
        if (written == null) {
            Log.e(TAG, "Failed to write $path")
            return false
        }

        // 2. Fix ownership/permissions so the HMA app can read & rewrite it
        //    (root-created files default to root:root).
        RootShell.exec("chown \$(stat -c %u /data/data/$HMA_PACKAGE):\$(stat -c %g /data/data/$HMA_PACKAGE) \"$path\"")
        RootShell.exec("chmod 660 \"$path\"")

        // 3. Verify by read-back.
        val readBack = RootShell.exec("cat \"$path\"")
        val ok = readBack != null && readBack.trim() == json.trim()
        if (ok) Log.i(TAG, "HMA config written & verified (${json.length} bytes)")
        else Log.e(TAG, "HMA config write-back mismatch")
        return ok
    }

    /** Current PID of the HMA app process, or null if it is not running. */
    fun pidOf(): Int? =
        RootShell.exec("pidof $HMA_PACKAGE")?.trim()?.toIntOrNull()

    /**
     * Force-stop HMA and relaunch it so it picks up the new config.json.
     * Returns the PID before stop and after start (null when not running).
     */
    fun restartHma(): HmaRestartResult {
        val stoppedPid = pidOf()
        RootShell.exec("am force-stop $HMA_PACKAGE")
        RootShell.exec("monkey -p $HMA_PACKAGE -c android.intent.category.LAUNCHER 1")
        // Poll for the relaunched process instead of guessing with fixed sleeps.
        var startedPid: Int? = null
        var attempts = 0
        while (startedPid == null && attempts < 10) {
            Thread.sleep(500)
            startedPid = pidOf()
            attempts++
        }
        return HmaRestartResult(stoppedPid, startedPid)
    }
}
