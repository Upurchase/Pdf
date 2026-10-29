package com.example.data.model

data class PdfItem(
    val uriString: String,
    val title: String,
    val filePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val lastReadPage: Int = 0,
    val lastOpenedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val isSample: Boolean = false
) {
    val formattedSize: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown size"
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format("%.1f MB", mb)
            } else {
                String.format("%.0f KB", kb)
            }
        }

    val progressPercent: Int
        get() {
            if (pageCount <= 0) return 0
            val progress = ((lastReadPage + 1).toFloat() / pageCount.toFloat()) * 100f
            return progress.toInt().coerceIn(0, 100)
        }
}
