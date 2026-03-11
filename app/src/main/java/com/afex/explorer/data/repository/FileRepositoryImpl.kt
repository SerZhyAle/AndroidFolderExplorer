package com.afex.explorer.data.repository

import com.afex.explorer.data.source.LocalFileDataSource
import com.afex.explorer.data.source.SafFileAccess
import com.afex.explorer.data.source.ShizukuFileAccess
import com.afex.explorer.domain.model.FileItem
import com.afex.explorer.domain.model.OperationProgress
import com.afex.explorer.domain.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AFEX_MOVE"

@Singleton
class FileRepositoryImpl @Inject constructor(
    private val dataSource: LocalFileDataSource,
    private val safFileAccess: SafFileAccess,
    private val shizukuFileAccess: ShizukuFileAccess
) : FileRepository {

    /**
     * True on API 30-33 when the user has granted MANAGE_EXTERNAL_STORAGE ("All files access").
     * On these API levels, the File API gains full read/write access to Android/data.
     * On API 34+ FUSE still blocks it even with the permission — Shizuku is required there.
     */
    private fun hasManageStorageAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            Environment.isExternalStorageManager()

    override suspend fun listDirectory(path: String): Result<List<FileItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    !safFileAccess.isRestrictedPath(path) -> {
                        val items = dataSource.listDirectory(path)
                        // Android 11+ FUSE hides data/ and obb/ from File.listFiles() when
                        // listing the Android/ parent — inject them manually if missing.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            injectHiddenAndroidSubdirs(path, items)
                        } else {
                            items
                        }
                    }
                    // Shizuku takes priority over MANAGE_EXTERNAL_STORAGE — Samsung Knox blocks
                    // File API even when permission is granted; shell access is always reliable.
                    shizukuFileAccess.hasPermission() -> shizukuFileAccess.listDirectory(path)
                    // API 30-33: MANAGE_EXTERNAL_STORAGE grants File API access to Android/data
                    // Some Samsung/custom ROMs still block it — catch and fall back to error
                    hasManageStorageAccess() -> {
                        try {
                            dataSource.listDirectory(path)
                        } catch (_: SecurityException) {
                            throw RestrictedAccessException(path)
                        }
                    }
                    safFileAccess.hasUriPermission(path) -> safFileAccess.listDirectory(path)
                    else -> throw RestrictedAccessException(path)
                }
            }
        }

    /**
     * On Android 11+, the FUSE overlay silently drops Android/data and Android/obb from
     * File.listFiles() results on the Android/ parent directory. Inject them so the user
     * can tap through to the restricted-access flow.
     */
    private fun injectHiddenAndroidSubdirs(path: String, items: List<FileItem>): List<FileItem> {
        val androidDir = Environment.getExternalStorageDirectory().absolutePath + "/Android"
        if (!path.trimEnd('/').equals(androidDir, ignoreCase = true)) return items
        val existingNames = items.map { it.name.lowercase() }.toSet()
        val injected = listOf("data", "obb").filter { it !in existingNames }.map { name ->
            val dir = File(androidDir, name)
            FileItem(
                name = name,
                path = dir.absolutePath,
                isDirectory = true,
                size = 0L,
                lastModified = if (dir.lastModified() > 0) dir.lastModified() else System.currentTimeMillis(),
                childCount = null
            )
        }
        if (injected.isEmpty()) return items
        return (items + injected)
            .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun copyFiles(sources: List<String>, destination: String): Flow<OperationProgress> =
        performOperation(sources, destination, deleteSource = false)

    override fun moveFiles(sources: List<String>, destination: String): Flow<OperationProgress> =
        performOperation(sources, destination, deleteSource = true)
    private fun performOperation(
        sources: List<String>,
        destination: String,
        deleteSource: Boolean
    ): Flow<OperationProgress> = flow {
        val destDir = File(destination)
        destDir.mkdirs()

        val totalBytes = sources.sumOf { path ->
            when {
                !safFileAccess.isRestrictedPath(path) ->
                    dataSource.calculateTotalSize(listOf(File(path)))
                shizukuFileAccess.hasPermission() ->
                    shizukuFileAccess.calculateTotalSize(listOf(path))
                hasManageStorageAccess() ->
                    dataSource.calculateTotalSize(listOf(File(path)))
                else ->
                    safFileAccess.calculateTotalSize(listOf(path))
            }
        }
        val totalFiles = sources.sumOf { path ->
            when {
                !safFileAccess.isRestrictedPath(path) ->
                    dataSource.countFiles(File(path))
                shizukuFileAccess.hasPermission() ->
                    shizukuFileAccess.countFiles(path)
                hasManageStorageAccess() ->
                    dataSource.countFiles(File(path))
                else ->
                    safFileAccess.countFiles(path)
            }
        }
        var processedFiles = 0
        var processedBytes = 0L

        emit(OperationProgress(totalFiles, 0, "", totalBytes, 0L))

        Log.d(TAG, "performOperation: deleteSource=$deleteSource dst=${destDir.absolutePath} totalFiles=$totalFiles totalBytes=$totalBytes sources=$sources")
        // Capture once — Shizuku IPC (pingBinder + checkSelfPermission) is not stable
        // under rapid repeated calls: successive invocations within the same coroutine can
        // return different results, so we snapshot the value before the loop.
        val hasShizukuPerm = shizukuFileAccess.hasPermission()
        Log.d(TAG, "API=${Build.VERSION.SDK_INT} hasManageStorage=${hasManageStorageAccess()} shizukuHasPerm=$hasShizukuPerm shizukuRunning=${shizukuFileAccess.isServiceRunning()}")

        for (sourcePath in sources) {
            currentCoroutineContext().ensureActive()
            val isRestricted = safFileAccess.isRestrictedPath(sourcePath)
            // Shizuku takes priority over MANAGE_EXTERNAL_STORAGE on restricted paths —
            // Samsung Knox blocks File API even when permission is granted, making shell cp/mv
            // the only reliable option. Only fall back to File API when Shizuku is unavailable.
            val useShizuku = isRestricted && hasShizukuPerm
            val useManageStorage = isRestricted && !useShizuku && hasManageStorageAccess()

            Log.d(TAG, "source=$sourcePath isRestricted=$isRestricted useShizuku=$useShizuku useManageStorage=$useManageStorage")

            if (useShizuku) {
                // Shizuku: one-shot shell copy — emit start + complete only
                Log.d(TAG, "[Shizuku] ${if (deleteSource) "mv" else "cp"} $sourcePath -> ${destDir.absolutePath}")
                emit(OperationProgress(totalFiles, processedFiles, File(sourcePath).name, totalBytes, processedBytes))
                try {
                    if (deleteSource) {
                        shizukuFileAccess.moveInto(sourcePath, destDir.absolutePath)
                    } else {
                        shizukuFileAccess.copyInto(sourcePath, destDir.absolutePath)
                    }
                    Log.d(TAG, "[Shizuku] operation succeeded")
                } catch (e: Exception) {
                    Log.e(TAG, "[Shizuku] operation FAILED", e)
                    throw e
                }
                processedFiles++
                emit(OperationProgress(totalFiles, processedFiles, File(sourcePath).name, totalBytes, processedBytes))
            } else {
                // useManageStorage → treat as unrestricted (File API), otherwise use SAF
                val passRestricted = isRestricted && !useManageStorage
                Log.d(TAG, "[FileAPI/SAF] copyRecursive passRestricted=$passRestricted")
                copyRecursive(sourcePath, destDir, isRestricted = passRestricted) { fileName, fileSize ->
                    processedFiles++
                    processedBytes += fileSize
                    Log.d(TAG, "  copied: $fileName size=$fileSize")
                    emit(
                        OperationProgress(totalFiles, processedFiles, fileName, totalBytes, processedBytes)
                    )
                }

                if (deleteSource) {
                    val destFile = File(destDir, File(sourcePath).name)
                    Log.d(TAG, "[delete] destFile=${destFile.absolutePath} exists=${destFile.exists()}")
                    if (destFile.exists()) {
                        if (isRestricted && !useManageStorage) {
                            Log.d(TAG, "[delete] via SAF: $sourcePath")
                            safFileAccess.deleteDocument(sourcePath)
                        } else {
                            Log.d(TAG, "[delete] via File API: $sourcePath")
                            val deleteResult = dataSource.deleteRecursively(File(sourcePath))
                            Log.d(TAG, "[delete] result=$deleteResult")
                        }
                    } else {
                        Log.w(TAG, "[delete] SKIPPED — dest file does not exist, copy may have failed silently")
                    }
                }
            }
        }

        emit(OperationProgress(totalFiles, totalFiles, "", totalBytes, totalBytes))
    }.flowOn(Dispatchers.IO)

    private suspend fun copyRecursive(
        sourcePath: String,
        destDir: File,
        isRestricted: Boolean,
        onFileCopied: suspend (fileName: String, fileSize: Long) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        val sourceName = sourcePath.substringAfterLast('/')
        val target = File(destDir, sourceName)

        if (isRestricted) {
            copyRecursiveSaf(sourcePath, target, onFileCopied)
        } else {
            copyRecursiveFile(File(sourcePath), target, onFileCopied)
        }
    }

    private suspend fun copyRecursiveFile(
        source: File,
        target: File,
        onFileCopied: suspend (fileName: String, fileSize: Long) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        Log.d(TAG, "copyRecursiveFile: src=${source.absolutePath} exists=${source.exists()} isDir=${source.isDirectory} canRead=${source.canRead()}")
        if (source.isDirectory) {
            val mkOk = target.mkdirs()
            Log.d(TAG, "  mkdir ${target.absolutePath} ok=$mkOk")
            onFileCopied(source.name, 0L)
            val children = source.listFiles()
            Log.d(TAG, "  listFiles=${children?.size ?: "null (BLOCKED)"}")
            if (children == null) {
                Log.e(TAG, "  listFiles() returned null — no read access despite permission? (Samsung Knox?)")
                return
            }
            for (child in children) {
                copyRecursiveFile(child, File(target, child.name), onFileCopied)
            }
        } else {
            Log.d(TAG, "  copyFile -> ${target.absolutePath} size=${source.length()}")
            try {
                dataSource.copyFile(source, target) { /* byte-level progress reserved */ }
                Log.d(TAG, "  copyFile OK, destExists=${target.exists()} destSize=${target.length()}")
            } catch (e: Exception) {
                Log.e(TAG, "  copyFile FAILED", e)
                throw e
            }
            onFileCopied(source.name, source.length())
        }
    }

    private suspend fun copyRecursiveSaf(
        sourcePath: String,
        target: File,
        onFileCopied: suspend (fileName: String, fileSize: Long) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        val sourceName = sourcePath.substringAfterLast('/')
        Log.d(TAG, "copyRecursiveSaf: src=$sourcePath")

        // Check if directory by trying to list children
        val children = try {
            safFileAccess.listDirectory(sourcePath)
        } catch (e: Exception) {
            Log.e(TAG, "  safListDirectory failed", e)
            null
        }
        Log.d(TAG, "  SAF children=${children?.size ?: "null"} for $sourceName")

        if (children != null && children.isNotEmpty()) {
            // Directory
            target.mkdirs()
            onFileCopied(sourceName, 0L)
            for (child in children) {
                copyRecursiveSaf(child.path, File(target, child.name), onFileCopied)
            }
        } else if (children != null) {
            // Empty directory
            target.mkdirs()
            onFileCopied(sourceName, 0L)
        } else {
            // File
            val inputStream = safFileAccess.openInputStream(sourcePath)
            dataSource.copyFromStream(inputStream, target) { /* byte-level progress reserved */ }
            onFileCopied(sourceName, target.length())
        }
    }

    override suspend fun deleteFiles(paths: List<String>): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                var deleted = 0
                for (path in paths) {
                    val isRestricted = safFileAccess.isRestrictedPath(path)
                    val success = when {
                        isRestricted && shizukuFileAccess.hasPermission() ->
                            shizukuFileAccess.deleteRecursively(path)
                        isRestricted && hasManageStorageAccess() ->
                            dataSource.deleteRecursively(File(path))
                        isRestricted ->
                            safFileAccess.deleteDocument(path)
                        else ->
                            dataSource.deleteRecursively(File(path))
                    }
                    if (success) deleted++
                }
                deleted
            }
        }
}

class RestrictedAccessException(val path: String) :
    Exception("Restricted directory requires additional permission: $path")
