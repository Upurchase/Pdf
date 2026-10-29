package com.example.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.local.PdfBookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.RecentPdfEntity
import com.example.data.model.PdfItem
import com.example.pdf.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PdfRepository(
    private val context: Context,
    private val pdfDao: PdfDao
) {

    val recentsFlow: Flow<List<PdfItem>> = pdfDao.getAllRecents().map { list ->
        list.map { entity ->
            PdfItem(
                uriString = entity.uriString,
                title = entity.title,
                filePath = entity.filePath,
                fileSizeBytes = entity.fileSizeBytes,
                pageCount = entity.pageCount,
                lastReadPage = entity.lastReadPage,
                lastOpenedTimestamp = entity.lastOpenedTimestamp,
                isFavorite = entity.isFavorite
            )
        }
    }

    val favoritesFlow: Flow<List<PdfItem>> = pdfDao.getFavoritePdfs().map { list ->
        list.map { entity ->
            PdfItem(
                uriString = entity.uriString,
                title = entity.title,
                filePath = entity.filePath,
                fileSizeBytes = entity.fileSizeBytes,
                pageCount = entity.pageCount,
                lastReadPage = entity.lastReadPage,
                lastOpenedTimestamp = entity.lastOpenedTimestamp,
                isFavorite = true
            )
        }
    }

    fun getBookmarksForPdf(pdfUri: String): Flow<List<PdfBookmarkEntity>> {
        return pdfDao.getBookmarksForPdf(pdfUri)
    }

    suspend fun addBookmark(pdfUri: String, pageIndex: Int, note: String) {
        pdfDao.insertBookmark(
            PdfBookmarkEntity(
                pdfUriString = pdfUri,
                pageIndex = pageIndex,
                note = note.ifBlank { "Page ${pageIndex + 1}" }
            )
        )
    }

    suspend fun removeBookmark(id: Long) {
        pdfDao.deleteBookmark(id)
    }

    suspend fun removeBookmarkByPage(pdfUri: String, pageIndex: Int) {
        pdfDao.deleteBookmarkByPage(pdfUri, pageIndex)
    }

    suspend fun toggleFavorite(uri: String, currentStatus: Boolean) {
        pdfDao.updateFavorite(uri, !currentStatus)
    }

    suspend fun deleteRecent(uri: String) {
        pdfDao.deleteRecent(uri)
    }

    suspend fun recordPdfOpened(
        uri: String,
        title: String,
        filePath: String?,
        fileSizeBytes: Long,
        pageCount: Int,
        lastReadPage: Int
    ) {
        val existing = pdfDao.getRecentByUri(uri)
        val isFav = existing?.isFavorite ?: false
        pdfDao.upsertRecent(
            RecentPdfEntity(
                uriString = uri,
                title = title,
                filePath = filePath,
                fileSizeBytes = fileSizeBytes,
                pageCount = pageCount,
                lastReadPage = lastReadPage,
                lastOpenedTimestamp = System.currentTimeMillis(),
                isFavorite = isFav
            )
        )
    }

    suspend fun updateReadingProgress(uri: String, pageIndex: Int) {
        pdfDao.updateReadingProgress(uri, pageIndex)
    }

    suspend fun getLastReadPage(uri: String): Int {
        return pdfDao.getRecentByUri(uri)?.lastReadPage ?: 0
    }

    fun getSamplePdfItem(): PdfItem {
        val sampleFile = SamplePdfGenerator.getOrGenerateSamplePdf(context)
        return PdfItem(
            uriString = Uri.fromFile(sampleFile).toString(),
            title = "Welcome & User Guide",
            filePath = sampleFile.absolutePath,
            fileSizeBytes = sampleFile.length(),
            pageCount = 4,
            lastReadPage = 0,
            lastOpenedTimestamp = System.currentTimeMillis(),
            isFavorite = false,
            isSample = true
        )
    }

    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getManageStorageIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
    }

    suspend fun scanDevicePdfs(): List<PdfItem> = withContext(Dispatchers.IO) {
        val pdfList = mutableListOf<PdfItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. Always include the starter guide
        val sample = getSamplePdfItem()
        pdfList.add(sample)
        sample.filePath?.let { seenPaths.add(it.lowercase()) }

        // 2. Query MediaStore
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA
            )

            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf'"
            val selectionArgs = arrayOf("application/pdf")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            val queryUri = MediaStore.Files.getContentUri("external")
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Document.pdf"
                    val size = cursor.getLong(sizeCol)
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val contentUri = ContentUris.withAppendedId(queryUri, id)

                    if (dataPath != null) {
                        seenPaths.add(dataPath.lowercase())
                    }

                    pdfList.add(
                        PdfItem(
                            uriString = contentUri.toString(),
                            title = name.removeSuffix(".pdf"),
                            filePath = dataPath,
                            fileSizeBytes = size,
                            pageCount = 0,
                            lastReadPage = 0,
                            lastOpenedTimestamp = 0L,
                            isFavorite = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("PdfRepository", "Error scanning MediaStore PDFs", e)
        }

        // 3. Direct File System Scan if storage permission is granted
        if (hasFullStorageAccess()) {
            val targetDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "Documents"),
                File(Environment.getExternalStorageDirectory(), "Books"),
                File(Environment.getExternalStorageDirectory(), "PDF"),
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
                File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Documents")
            )

            for (dir in targetDirs) {
                if (dir.exists() && dir.isDirectory) {
                    scanDirectoryForPdfs(dir, pdfList, seenPaths, maxDepth = 3)
                }
            }
        }

        return@withContext pdfList
    }

    private fun scanDirectoryForPdfs(
        dir: File,
        results: MutableList<PdfItem>,
        seenPaths: MutableSet<String>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectoryForPdfs(file, results, seenPaths, maxDepth, currentDepth + 1)
            } else if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                val pathLower = file.absolutePath.lowercase()
                if (!seenPaths.contains(pathLower)) {
                    seenPaths.add(pathLower)
                    results.add(
                        PdfItem(
                            uriString = Uri.fromFile(file).toString(),
                            title = file.nameWithoutExtension,
                            filePath = file.absolutePath,
                            fileSizeBytes = file.length(),
                            pageCount = 0,
                            lastReadPage = 0,
                            lastOpenedTimestamp = file.lastModified(),
                            isFavorite = false
                        )
                    )
                }
            }
        }
    }

    fun resolveUriMetadata(uri: Uri): Pair<String, Long> {
        var name = "Document"
        var size = 0L

        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                name = file.nameWithoutExtension
                size = file.length()
            }
            return Pair(name, size)
        }

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)?.removeSuffix(".pdf") ?: "Document"
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfRepository", "Failed to resolve URI metadata", e)
            uri.lastPathSegment?.let { segment ->
                name = segment.removeSuffix(".pdf")
            }
        }

        return Pair(name, size)
    }
}
