package com.afex.explorer.data.source

import com.afex.explorer.domain.model.FileItem
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileDataSource @Inject constructor() {

    fun listDirectory(path: String): List<FileItem> {
        val dir = File(path)
        val files = dir.listFiles()
            ?: throw SecurityException("Cannot access directory: $path. Check storage permissions.")

        return files.map { file ->
            FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                childCount = if (file.isDirectory) file.listFiles()?.size else null
            )
        }.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun copyFile(source: File, destination: File, onBytesWritten: (Long) -> Unit) {
        if (source.isDirectory) {
            destination.mkdirs()
            return
        }
        destination.parentFile?.mkdirs()
        source.inputStream().buffered().use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    onBytesWritten(bytesRead.toLong())
                }
            }
        }
    }

    fun copyFromStream(input: InputStream, destination: File, onBytesWritten: (Long) -> Unit) {
        destination.parentFile?.mkdirs()
        input.buffered().use { bis ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var bytesRead: Int
                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    onBytesWritten(bytesRead.toLong())
                }
            }
        }
    }

    fun deleteRecursively(file: File): Boolean = file.deleteRecursively()

    fun calculateTotalSize(files: List<File>): Long =
        files.sumOf { calculateSize(it) }

    fun countFiles(file: File): Int = when {
        file.isFile -> 1
        file.isDirectory -> {
            val children = file.listFiles() ?: emptyArray()
            1 + children.sumOf { countFiles(it) }
        }
        else -> 0
    }

    private fun calculateSize(file: File): Long = when {
        file.isFile -> file.length()
        file.isDirectory -> (file.listFiles() ?: emptyArray()).sumOf { calculateSize(it) }
        else -> 0L
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 8 * 1024
    }
}
