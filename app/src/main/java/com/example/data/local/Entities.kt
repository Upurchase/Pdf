package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_pdfs")
data class RecentPdfEntity(
    @PrimaryKey
    val uriString: String,
    val title: String,
    val filePath: String?,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val lastReadPage: Int,
    val lastOpenedTimestamp: Long,
    val isFavorite: Boolean = false
)

@Entity(tableName = "pdf_bookmarks")
data class PdfBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfUriString: String,
    val pageIndex: Int,
    val note: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)
