package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfEngine(private val context: Context) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var tempCachedFile: File? = null

    var pageCount: Int = 0
        private set

    var documentTitle: String = ""
        private set

    // Cache up to ~32MB of bitmaps in memory
    private val maxCacheMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt() / 8
    private val pageBitmapCache = object : LruCache<Int, Bitmap>(maxCacheMemory) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val thumbnailCache = object : LruCache<Int, Bitmap>(16 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    suspend fun openDocument(uri: Uri, title: String): Boolean = withContext(Dispatchers.IO) {
        close()
        documentTitle = title

        try {
            val pfd: ParcelFileDescriptor? = if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists()) return@withContext false
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                // To guarantee seekability and support all content providers (Downloads, Drive, MediaStore)
                try {
                    val directPfd = context.contentResolver.openFileDescriptor(uri, "r")
                    // Test if seekable by trying to create PdfRenderer directly
                    if (directPfd != null) {
                        try {
                            val testRenderer = PdfRenderer(directPfd)
                            testRenderer.close()
                            // Re-open fresh
                            context.contentResolver.openFileDescriptor(uri, "r")
                        } catch (e: Exception) {
                            directPfd.close()
                            null // Fallback to copy
                        }
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            val finalPfd = if (pfd != null) {
                pfd
            } else {
                // Copy to cache file
                val cacheFile = File(context.cacheDir, "temp_reading_doc_${System.currentTimeMillis()}.pdf")
                tempCachedFile = cacheFile
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }

            parcelFileDescriptor = finalPfd
            pdfRenderer = PdfRenderer(finalPfd)
            pageCount = pdfRenderer?.pageCount ?: 0
            Log.d("PdfEngine", "Opened document with $pageCount pages")
            return@withContext pageCount > 0
        } catch (e: Exception) {
            Log.e("PdfEngine", "Error opening PDF document", e)
            close()
            return@withContext false
        }
    }

    suspend fun openLocalFile(file: File, title: String): Boolean = withContext(Dispatchers.IO) {
        close()
        documentTitle = title
        try {
            if (!file.exists()) return@withContext false
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            parcelFileDescriptor = pfd
            pdfRenderer = PdfRenderer(pfd)
            pageCount = pdfRenderer?.pageCount ?: 0
            return@withContext pageCount > 0
        } catch (e: Exception) {
            Log.e("PdfEngine", "Error opening local PDF file", e)
            close()
            return@withContext false
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCount) return@withContext null

        synchronized(pageBitmapCache) {
            val cached = pageBitmapCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        val renderer = pdfRenderer ?: return@withContext null
        try {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val width = page.width
                val height = page.height

                // Calculate scaled dimensions for crisp high-density display
                val scale = (targetWidth.toFloat() / width.toFloat()).coerceAtLeast(1f).coerceAtMost(3f)
                val destWidth = (width * scale).toInt()
                val destHeight = (height * scale).toInt()

                val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                // Initialize background to white
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                synchronized(pageBitmapCache) {
                    pageBitmapCache.put(pageIndex, bitmap)
                }
                return@withContext bitmap
            }
        } catch (e: Exception) {
            Log.e("PdfEngine", "Error rendering page $pageIndex", e)
            return@withContext null
        }
    }

    suspend fun renderThumbnail(pageIndex: Int, targetWidth: Int = 220): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCount) return@withContext null

        synchronized(thumbnailCache) {
            val cached = thumbnailCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        val renderer = pdfRenderer ?: return@withContext null
        try {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val scale = targetWidth.toFloat() / page.width.toFloat()
                val destWidth = (page.width * scale).toInt().coerceAtLeast(1)
                val destHeight = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                synchronized(thumbnailCache) {
                    thumbnailCache.put(pageIndex, bitmap)
                }
                return@withContext bitmap
            }
        } catch (e: Exception) {
            Log.e("PdfEngine", "Error rendering thumbnail $pageIndex", e)
            return@withContext null
        }
    }

    fun close() {
        try {
            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()
            pdfRenderer?.close()
            pdfRenderer = null
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null
            tempCachedFile?.delete()
            tempCachedFile = null
            pageCount = 0
            documentTitle = ""
        } catch (e: Exception) {
            Log.e("PdfEngine", "Error during PdfEngine close", e)
        }
    }
}
