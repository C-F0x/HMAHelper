package org.cf0x.hma.helper.root

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Minimal root shell helper built on `su` (Magisk / KernelSU / APatch / legacy).
 * Kept dependency-free on purpose: commands run via Runtime.exec, matching the
 * project's existing style (see preset/PackageScanner).
 */
object RootShell {

    private const val TAG = "RootShell"

    /** Cap accumulated stdout so huge outputs (e.g. `dumpsys package`) can't OOM us. */
    private const val MAX_OUTPUT_CHARS = 64 * 1024 * 1024

    private fun execRaw(args: Array<String>, input: String? = null, timeoutMs: Long = 20000): String? {
        return runCatching {
            val proc = Runtime.getRuntime().exec(args)
            if (input != null) {
                proc.outputStream.use { it.write(input.toByteArray(StandardCharsets.UTF_8)) }
            } else {
                proc.outputStream.close()
            }
            val out = StringBuilder()
            val err = StringBuilder()
            val outThread = Thread {
                BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8)).use { br ->
                    var line: String?
                    var total = 0
                    while (br.readLine().also { line = it } != null) {
                        if (total > MAX_OUTPUT_CHARS) break
                        out.append(line).append('\n')
                        total += line?.length ?: 0
                    }
                }
            }
            outThread.isDaemon = true
            outThread.start()
            val errThread = Thread {
                BufferedReader(InputStreamReader(proc.errorStream, StandardCharsets.UTF_8)).use { br ->
                    var line: String?
                    var total = 0
                    while (br.readLine().also { line = it } != null) {
                        if (total > MAX_OUTPUT_CHARS / 16) break
                        err.append(line).append('\n')
                        total += line?.length ?: 0
                    }
                }
            }
            errThread.isDaemon = true
            errThread.start()
            if (!proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                proc.destroy()
                Log.w(TAG, "Command timed out: ${args.joinToString(" ")}")
                return null
            }
            outThread.join(2000)
            errThread.join(2000)
            if (err.isNotBlank()) Log.w(TAG, "stderr: ${err.toString().trim()}")
            out.toString().trim()
        }.onFailure { e ->
            Log.w(TAG, "exec failed: ${e.message}")
        }.getOrNull()
    }

    /** Run a command via `su -c ...` and return stdout, or null on failure. */
    fun exec(command: String, input: String? = null, timeoutMs: Long = 20000): String? =
        execRaw(arrayOf("su", "-c", command), input = input, timeoutMs = timeoutMs)

    /** Run a command without su (useful for fallback checks). */
    fun execDirect(command: String): String? =
        execRaw(arrayOf("sh", "-c", command))

    /** True if the app has been granted root by the active su implementation. */
    fun isRootAvailable(): Boolean {
        val out = exec("id") ?: return false
        return out.contains("uid=0")
    }
}
