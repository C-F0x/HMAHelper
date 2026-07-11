package org.cf0x.hma.helper.preset

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Multi-path package name scanner.
 * Cross-validates through several independent PackageManager entry points
 * to defeat apps that only hook [PackageManager.getInstalledPackages].
 */
object PackageScanner {

    private const val TAG = "PackageScanner"

    fun scan(context: Context, knownPackages: Set<String>): Set<String> {
        val found = mutableSetOf<String>()
        val pm = context.packageManager

        // Path 1: shell `pm list packages` (bypasses Java PM hooks entirely)
        runCatching {
            val p = Runtime.getRuntime().exec("pm list packages")
            BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8)).use { br ->
                br.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("package:") && trimmed.length > 8) {
                        val pkg = trimmed.substring(8).trim()
                        found.add(pkg)
                    }
                }
            }
            p.destroy()
        }.onFailure { e ->
            Log.w(TAG, "pm list packages failed: ${e.message}")
        }

        // Path 2: getInstalledApplications(0) — flag 0 may bypass some hooks
        runCatching {
            pm.getInstalledApplications(0).forEach { found.add(it.packageName) }
        }.onFailure { e ->
            Log.w(TAG, "getInstalledApplications failed: ${e.message}")
        }

        // Path 3: getInstalledPackages(0) — flag 0 variant
        runCatching {
            pm.getInstalledPackages(0).forEach { found.add(it.packageName) }
        }.onFailure { e ->
            Log.w(TAG, "getInstalledPackages failed: ${e.message}")
        }

        // Path 4: queryIntentActivities(MAIN, MATCH_ALL) — Intent-based
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN)
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            list.forEach { found.add(it.activityInfo.packageName) }
        }.onFailure { e ->
            Log.w(TAG, "queryIntentActivities failed: ${e.message}")
        }

        // Path 5: per-package single-entry checks for the known set
        for (pkg in knownPackages) {
            if (pkg in found) continue  // already found, skip

            val visible = runCatching {
                // getPackageUid
                try {
                    pm.getPackageUid(pkg, 0)
                    return@runCatching true
                } catch (_: PackageManager.NameNotFoundException) { }

                // getLaunchIntentForPackage
                if (pm.getLaunchIntentForPackage(pkg) != null) return@runCatching true

                // try-catch getPackageInfo
                try {
                    pm.getPackageInfo(pkg, 0)
                    return@runCatching true
                } catch (_: PackageManager.NameNotFoundException) { }

                false
            }.getOrDefault(false)

            if (visible) found.add(pkg)
        }

        // Filter: only return packages that are in the known set, unless none were specified
        val result = if (knownPackages.isEmpty()) found else found.filter { it in knownPackages }.toSet()
        Log.i(TAG, "Scan: ${found.size} found → ${result.size} visible")

        return result
    }
}
