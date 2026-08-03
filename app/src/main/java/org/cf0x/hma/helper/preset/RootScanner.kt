package org.cf0x.hma.helper.preset

import android.util.Log
import org.cf0x.hma.helper.root.RootShell

/**
 * Per-package facts collected via root, used by the root detection path of
 * every preset. Unlike [ApplicationInfo] obtained from PackageManager, these
 * are not subject to package visibility or metadata restrictions on newer
 * Android versions.
 */
data class RootAppInfo(
    val packageName: String,
    val apkPath: String?,
    val metaKeys: Set<String>,
    val hasAccessibilityService: Boolean,
    val hasLibXposedProvider: Boolean = false,
    val isOverlay: Boolean = false
)

/**
 * Root-powered package scanner.
 *
 * Strategy (two root calls total, no APK copies):
 *  1. `pm list packages -f`  — full package enumeration with APK paths,
 *     unaffected by package visibility (fixes missing apps on Android 17+).
 *  2. `dumpsys package`      — per-package application meta-data keys and
 *     service permissions (BIND_ACCESSIBILITY_SERVICE), parsed from the
 *     stable `Packages:` main table.
 *
 * All six presets can then be evaluated purely from these facts.
 */
object RootScanner {

    private const val TAG = "RootScanner"

    private const val PERM_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"

    fun scan(): Map<String, RootAppInfo> {
        val apkPaths = scanPackagesWithPaths()
        val meta = scanMetaData()

        val result = linkedMapOf<String, RootAppInfo>()
        val allPkgs = apkPaths.keys + meta.keys
        for (pkg in allPkgs) {
            val apkPath = apkPaths[pkg]
            result[pkg] = RootAppInfo(
                packageName = pkg,
                apkPath = apkPath,
                metaKeys = meta[pkg]?.metaKeys ?: emptySet(),
                hasAccessibilityService = meta[pkg]?.hasAccessibilityService ?: false,
                hasLibXposedProvider = meta[pkg]?.hasLibXposedProvider ?: false,
                // RRO detection: dumpsys marks it with "overlay target:", and
                // system overlays always live under an /overlay/ install path
                // (fallback for dumpsys format differences on newer Android).
                isOverlay = meta[pkg]?.isOverlay == true || apkPath?.contains("/overlay/") == true
            )
        }
        Log.i(TAG, "Root scan: ${result.size} packages, ${meta.values.count { it.metaKeys.isNotEmpty() }} with meta-data")
        return result
    }

    /** `pm list packages -f` → package name → APK path. */
    private fun scanPackagesWithPaths(): Map<String, String> {
        val out = RootShell.exec("pm list packages -f") ?: return emptyMap()
        val map = linkedMapOf<String, String>()
        out.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("package:")) return@forEach
            val body = trimmed.removePrefix("package:")
            val eq = body.lastIndexOf('=')
            if (eq <= 0) return@forEach
            val pkg = body.substring(eq + 1).trim()
            val path = body.substring(0, eq).trim()
            if (pkg.isNotBlank() && path.isNotBlank()) map[pkg] = path
        }
        return map
    }

    /** `dumpsys package` → package name → meta-data keys + accessibility flag. */
    private fun scanMetaData(): Map<String, MetaData> {
        val out = RootShell.exec("dumpsys package", timeoutMs = 120000) ?: return emptyMap()
        return parseDumpsys(out)
    }

    private class MetaData(
        val metaKeys: MutableSet<String> = mutableSetOf(),
        var hasAccessibilityService: Boolean = false,
        var hasLibXposedProvider: Boolean = false,
        var isOverlay: Boolean = false
    )

    private enum class Section { NONE, PACKAGES, PROVIDERS }

    private fun parseDumpsys(output: String): Map<String, MetaData> {
        val result = linkedMapOf<String, MetaData>()
        var section = Section.NONE
        var currentPkg: String? = null

        for (line in output.lineSequence()) {
            if (line.isEmpty()) continue

            // Top-level section headers are unindented and end with ':'.
            if (!line[0].isWhitespace()) {
                section = when (line.trim()) {
                    "Packages:" -> Section.PACKAGES
                    "Registered ContentProviders:" -> Section.PROVIDERS
                    else -> Section.NONE
                }
                currentPkg = null
                continue
            }

            when (section) {
                Section.PACKAGES -> {
                    // "  Package [com.example] (hash):" — start of a package block.
                    // "  Package [" is 11 chars, so the name starts at index 11.
                    if (line.startsWith("  Package [")) {
                        val close = line.indexOf(']', 11)
                        val pkg = if (close > 0) line.substring(11, close) else null
                        currentPkg = pkg
                        if (pkg != null) result.getOrPut(pkg) { MetaData() }
                        continue
                    }

                    val pkg = currentPkg ?: continue
                    val meta = result[pkg] ?: continue

                    val trimmed = line.trim()
                    if (trimmed.startsWith("meta-data: ")) {
                        val key = trimmed.removePrefix("meta-data: ").substringBefore('=')
                        if (key.isNotBlank()) meta.metaKeys.add(key)
                    } else if (trimmed.contains("android:permission=$PERM_ACCESSIBILITY")) {
                        meta.hasAccessibilityService = true
                    } else if (trimmed.startsWith("overlay target:")) {
                        // Runtime Resource Overlay (RRO) — not a real app.
                        meta.isOverlay = true
                    }
                }

                Section.PROVIDERS -> {
                    // "  com.example/io.github.libxposed.service.XposedProvider:"
                    // libxposed (next-gen Xposed) modules declare this provider.
                    // Match only the component-name line (ends with ':', no '{')
                    // and NOT the indented "  Provider{hash pkg/cls}" line below it.
                    val trimmed = line.trim()
                    if (trimmed.endsWith(":") &&
                        !trimmed.contains('{') &&
                        trimmed.contains("io.github.libxposed.service.") &&
                        (trimmed.contains("XposedProvider") || trimmed.contains("XposedEntryProvider"))
                    ) {
                        val pkg = trimmed.substringBefore('/').substringBefore(' ')
                        if (pkg.isNotBlank() && pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' }) {
                            result.getOrPut(pkg) { MetaData() }.hasLibXposedProvider = true
                        }
                    }
                }

                Section.NONE -> {}
            }
        }
        return result
    }
}
