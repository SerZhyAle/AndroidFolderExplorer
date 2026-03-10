package com.afex.explorer.data.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.afex.explorer.domain.model.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles file access for restricted directories (Android/data, Android/obb)
 * on API 30+ using Storage Access Framework (SAF).
 *
 * MANAGE_EXTERNAL_STORAGE does NOT grant File API access to these directories.
 * SAF tree URI permission is required as a one-time user grant.
 */
@Singleton
class SafFileAccess @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isRestrictedPath(path: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX)
        return RESTRICTED_DIRS.any { relative == it || relative.startsWith("$it/") }
    }

    fun hasUriPermission(path: String): Boolean {
        return getPersistedTreeUri(path) != null
    }

    fun buildSafIntent(path: String): Intent {
        val restrictedBase = getRestrictedBase(path)
        val docId = "primary:${restrictedBase?.removePrefix("/") ?: "Android/data"}"
        val initialUri = DocumentsContract.buildDocumentUri(AUTHORITY, docId)

        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
    }

    /** Returns just the initial URI hint for [ActivityResultContracts.OpenDocumentTree]. */
    fun buildSafInitialUri(path: String): Uri {
        val restrictedBase = getRestrictedBase(path)
        val docId = "primary:${restrictedBase?.removePrefix("/") ?: "Android/data"}"
        return DocumentsContract.buildDocumentUri(AUTHORITY, docId)
    }

    fun persistTreeUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun listDirectory(path: String): List<FileItem> {
        val treeUri = getPersistedTreeUri(path)
            ?: throw SecurityException("No SAF permission for $path")

        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX).removePrefix("/")
        val docId = "primary:$relative"
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val items = mutableListOf<FileItem>()
        context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                val mimeType = cursor.getString(mimeIdx)
                val size = cursor.getLong(sizeIdx)
                val modified = cursor.getLong(modifiedIdx)
                val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                items.add(
                    FileItem(
                        name = name,
                        path = "$path/$name",
                        isDirectory = isDir,
                        size = if (!isDir) size else 0L,
                        lastModified = modified,
                        childCount = null
                    )
                )
            }
        }

        return items.sortedWith(
            compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    fun openInputStream(path: String): InputStream {
        val treeUri = getPersistedTreeUri(path)
            ?: throw SecurityException("No SAF permission for $path")

        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX).removePrefix("/")
        val docId = "primary:$relative"
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

        return context.contentResolver.openInputStream(documentUri)
            ?: throw java.io.IOException("Cannot open input stream for $path")
    }

    fun deleteDocument(path: String): Boolean {
        val treeUri = getPersistedTreeUri(path) ?: return false
        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX).removePrefix("/")
        val docId = "primary:$relative"
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, documentUri)
        } catch (_: Exception) {
            false
        }
    }

    fun calculateTotalSize(paths: List<String>): Long =
        paths.sumOf { calculateSizeRecursive(it) }

    fun countFiles(path: String): Int {
        val treeUri = getPersistedTreeUri(path) ?: return 0
        return countFilesRecursive(treeUri, path)
    }

    private fun countFilesRecursive(treeUri: Uri, path: String): Int {
        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX).removePrefix("/")
        val docId = "primary:$relative"
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        var count = 1
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeIdx)
                val name = cursor.getString(nameIdx)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    count += countFilesRecursive(treeUri, "$path/$name")
                } else {
                    count++
                }
            }
        }
        return count
    }

    private fun calculateSizeRecursive(path: String): Long {
        val treeUri = getPersistedTreeUri(path) ?: return 0L
        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX).removePrefix("/")
        val docId = "primary:$relative"

        // Check if this is a file or directory
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        var mime: String? = null
        var size = 0L
        context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                mime = cursor.getString(0)
                size = cursor.getLong(1)
            }
        }

        if (mime != DocumentsContract.Document.MIME_TYPE_DIR) return size

        // Directory — recurse
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        var total = 0L
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)
                val childMime = cursor.getString(mimeIdx)
                val childSize = cursor.getLong(sizeIdx)
                if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    total += calculateSizeRecursive("$path/$name")
                } else {
                    total += childSize
                }
            }
        }
        return total
    }

    private fun getPersistedTreeUri(path: String): Uri? {
        val restrictedBase = getRestrictedBase(path) ?: return null
        val treeDocId = "primary:${restrictedBase.removePrefix("/")}"
        val expectedTreeUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, treeDocId)

        return context.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == expectedTreeUri && it.isReadPermission }
            ?.uri
    }

    private fun getRestrictedBase(path: String): String? {
        val relative = path.removePrefix(EXTERNAL_STORAGE_PREFIX)
        return RESTRICTED_DIRS.firstOrNull { relative == it || relative.startsWith("$it/") }
    }

    companion object {
        private const val EXTERNAL_STORAGE_PREFIX = "/storage/emulated/0"
        private const val AUTHORITY = "com.android.externalstorage.documents"
        private val RESTRICTED_DIRS = listOf("/Android/data", "/Android/obb")
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
