package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecents(): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavoritePdfs(): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE uriString = :uri LIMIT 1")
    suspend fun getRecentByUri(uri: String): RecentPdfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(recent: RecentPdfEntity)

    @Query("UPDATE recent_pdfs SET lastReadPage = :page, lastOpenedTimestamp = :timestamp WHERE uriString = :uri")
    suspend fun updateReadingProgress(uri: String, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE recent_pdfs SET isFavorite = :isFavorite WHERE uriString = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("DELETE FROM recent_pdfs WHERE uriString = :uri")
    suspend fun deleteRecent(uri: String)

    @Query("SELECT * FROM pdf_bookmarks WHERE pdfUriString = :uri ORDER BY pageIndex ASC")
    fun getBookmarksForPdf(uri: String): Flow<List<PdfBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: PdfBookmarkEntity)

    @Query("DELETE FROM pdf_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM pdf_bookmarks WHERE pdfUriString = :uri AND pageIndex = :pageIndex")
    suspend fun deleteBookmarkByPage(uri: String, pageIndex: Int)
}
