package com.sza.androidfolderexplorer.data.source

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.sza.androidfolderexplorer.domain.model.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

private const val STAG = "AFEX_SHIZUKU"

/**
 * File access via Shizuku privileged shell.
 *
 * Shizuku runs as the `shell` user (uid 2000) via ADB or root — it has access to
 * Android/data and Android/obb bypassing the FUSE overlay that blocks
 * MANAGE_EXTERNAL_STORAGE and SAF on Android 14+.
 *
 * User flow:
 *   1. Install Shizuku from Play Store, F-Droid, or shizuku.rikka.app
 *   2. Start Shizuku service (one-time ADB or Wireless ADB in Developer Options)
 *   3. Grant permission when prompted by this app
 */
@Singleton
class ShizukuFileAccess @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Shizuku APK is installed on the device (service may or may not be running). */
    fun isApkInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    /** Shizuku service binder is alive (APK installed AND service started). */
    fun isServiceRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        isServiceRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestPermission() {
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    fun listDirectory(path: String): List<FileItem> {
        // stat -c format: fullpath|type|size|modified_unix_seconds
        val output = runShell(
            "find ${q(path)} -maxdepth 1 -mindepth 1 -exec stat -c '%n|%F|%s|%Y' {} \\; 2>/dev/null"
        )
        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { parseStat(it) }
            .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun calculateTotalSize(paths: List<String>): Long =
        paths.sumOf { path ->
            runShell("du -sb ${q(path)} 2>/dev/null | cut -f1")
                .trim().toLongOrNull() ?: 0L
        }

    fun countFiles(path: String): Int =
        runShell("find ${q(path)} 2>/dev/null | wc -l")
            .trim().toIntOrNull()?.coerceAtLeast(1) ?: 1

    // Copies source into destDir (same semantics as `cp -r src destDir/`)
    fun copyInto(sourcePath: String, destDirPath: String) {
        val exit = runShellWithExit("cp -r ${q(sourcePath)} ${q(destDirPath)}")
        if (exit != 0) throw Exception("Shizuku cp failed for $sourcePath (exit $exit)")
    }

    fun moveInto(sourcePath: String, destDirPath: String) {
        val exit = runShellWithExit("mv ${q(sourcePath)} ${q(destDirPath)}")
        if (exit != 0) throw Exception("Shizuku mv failed for $sourcePath (exit $exit)")
    }

    fun deleteRecursively(path: String): Boolean =
        runShellWithExit("rm -rf ${q(path)}") == 0

    private fun parseStat(line: String): FileItem? {
        val parts = line.split("|")
        if (parts.size < 4) return null
        val fullPath = parts[0].trim()
        val type = parts[1].trim()
        val size = parts[2].trim().toLongOrNull() ?: 0L
        val modifiedSec = parts[3].trim().toLongOrNull() ?: 0L
        val name = fullPath.substringAfterLast('/')
        if (name.isEmpty()) return null
        val isDir = type == "directory"
        return FileItem(
            name = name,
            path = fullPath,
            isDirectory = isDir,
            size = if (!isDir) size else 0L,
            lastModified = modifiedSec * 1000L,
            childCount = null
        )
    }

    @Suppress("DEPRECATION")
    private fun runShell(command: String): String = try {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    } catch (_: Exception) { "" }

    @Suppress("DEPRECATION")
    private fun runShellWithExit(command: String): Int = try {
        Log.d(STAG, "runShellWithExit: $command")
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val stderr = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (stderr.isNotBlank()) Log.w(STAG, "stderr: $stderr")
        Log.d(STAG, "exit=$exit")
        exit
    } catch (e: Exception) {
        Log.e(STAG, "runShellWithExit exception", e)
        -1
    }

    // Single-quote a shell argument, escaping internal single quotes
    private fun q(path: String): String = "'${path.replace("'", "'\\''")}'"

    companion object {
        const val REQUEST_CODE = 1001
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
