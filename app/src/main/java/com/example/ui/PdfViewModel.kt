package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfBookmarkEntity
import com.example.data.local.PdfDatabase
import com.example.data.model.PdfItem
import com.example.data.repository.PdfRepository
import com.example.pdf.PdfEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReadingTheme {
    DAY, SEPIA, NIGHT
}

enum class ScrollViewMode {
    CONTINUOUS, PAGER
}

data class LibraryUiState(
    val selectedTab: Int = 0, // 0 = Device, 1 = Recent, 2 = Starred
    val searchQuery: String = "",
    val isScanning: Boolean = false,
    val deviceFiles: List<PdfItem> = emptyList(),
    val isSearching: Boolean = false,
    val hasStoragePermission: Boolean = false
)

data class ReaderUiState(
    val currentItem: PdfItem? = null,
    val isLoading: Boolean = false,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val readingTheme: ReadingTheme = ReadingTheme.DAY,
    val viewMode: ScrollViewMode = ScrollViewMode.CONTINUOUS,
    val areControlsVisible: Boolean = true,
    val bookmarks: List<PdfBookmarkEntity> = emptyList(),
    val isCurrentPageBookmarked: Boolean = false,
    val showJumpDialog: Boolean = false,
    val showThumbnailsSheet: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showDocDetailsDialog: Boolean = false,
    val error: String? = null
)

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PdfDatabase.getInstance(application)
    val repository = PdfRepository(application, database.pdfDao())
    val pdfEngine = PdfEngine(application)

    private val _libraryState = MutableStateFlow(LibraryUiState())
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _readerState = MutableStateFlow(ReaderUiState())
    val readerState: StateFlow<ReaderUiState> = _readerState.asStateFlow()

    val recentsFlow = repository.recentsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favoritesFlow = repository.favoritesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private var bookmarksJob: Job? = null

    init {
        scanDevicePdfs()
    }

    fun selectTab(index: Int) {
        _libraryState.update { it.copy(selectedTab = index) }
    }

    fun setSearchQuery(query: String) {
        _libraryState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _libraryState.update {
            val next = !it.isSearching
            it.copy(isSearching = next, searchQuery = if (!next) "" else it.searchQuery)
        }
    }

    fun checkStoragePermission() {
        val hasPermission = repository.hasFullStorageAccess()
        _libraryState.update { it.copy(hasStoragePermission = hasPermission) }
    }

    fun getManageStorageIntent(): Intent = repository.getManageStorageIntent()

    fun scanDevicePdfs() {
        viewModelScope.launch {
            val hasPermission = repository.hasFullStorageAccess()
            _libraryState.update { it.copy(isScanning = true, hasStoragePermission = hasPermission) }
            val files = repository.scanDevicePdfs()
            _libraryState.update { it.copy(deviceFiles = files, isScanning = false, hasStoragePermission = hasPermission) }
        }
    }

    fun openPdfFromUri(uri: Uri, optionalTitle: String? = null) {
        viewModelScope.launch {
            _readerState.update { it.copy(isLoading = true, error = null) }

            // Take persistable permission if content scheme
            if (uri.scheme == "content") {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    // Ignore if permission cannot be persisted (e.g. from ACTION_VIEW)
                }
            }

            val (resolvedName, size) = repository.resolveUriMetadata(uri)
            val title = optionalTitle ?: resolvedName

            val success = pdfEngine.openDocument(uri, title)
            if (!success) {
                _readerState.update {
                    it.copy(
                        isLoading = false,
                        error = "Unable to open PDF document. The file might be corrupted or password protected."
                    )
                }
                return@launch
            }

            val pageCount = pdfEngine.pageCount
            val savedLastPage = repository.getLastReadPage(uri.toString())
            val initialPage = if (savedLastPage in 0 until pageCount) savedLastPage else 0

            val item = PdfItem(
                uriString = uri.toString(),
                title = title,
                filePath = if (uri.scheme == "file") uri.path else null,
                fileSizeBytes = size,
                pageCount = pageCount,
                lastReadPage = initialPage,
                lastOpenedTimestamp = System.currentTimeMillis()
            )

            // Save to recents in DB
            repository.recordPdfOpened(
                uri = uri.toString(),
                title = title,
                filePath = item.filePath,
                fileSizeBytes = size,
                pageCount = pageCount,
                lastReadPage = initialPage
            )

            // Observe bookmarks
            observeBookmarks(uri.toString())

            _readerState.update {
                it.copy(
                    currentItem = item,
                    isLoading = false,
                    pageCount = pageCount,
                    currentPage = initialPage,
                    areControlsVisible = true,
                    error = null
                )
            }
        }
    }

    fun openPdfItem(item: PdfItem) {
        openPdfFromUri(Uri.parse(item.uriString), item.title)
    }

    fun openSampleGuide() {
        val sampleItem = repository.getSamplePdfItem()
        openPdfItem(sampleItem)
    }

    private fun observeBookmarks(uri: String) {
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            repository.getBookmarksForPdf(uri).collect { list ->
                _readerState.update { current ->
                    val isBookmarked = list.any { it.pageIndex == current.currentPage }
                    current.copy(bookmarks = list, isCurrentPageBookmarked = isBookmarked)
                }
            }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        if (pageIndex == _readerState.value.currentPage) return
        val clamped = pageIndex.coerceIn(0, (_readerState.value.pageCount - 1).coerceAtLeast(0))

        val currentUri = _readerState.value.currentItem?.uriString
        _readerState.update { state ->
            val isBm = state.bookmarks.any { it.pageIndex == clamped }
            state.copy(currentPage = clamped, isCurrentPageBookmarked = isBm)
        }

        if (currentUri != null) {
            viewModelScope.launch {
                repository.updateReadingProgress(currentUri, clamped)
            }
        }
    }

    fun toggleControls() {
        _readerState.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _readerState.update { it.copy(readingTheme = theme) }
    }

    fun setViewMode(mode: ScrollViewMode) {
        _readerState.update { it.copy(viewMode = mode) }
    }

    fun toggleBookmarkCurrentPage(note: String = "") {
        val state = _readerState.value
        val uri = state.currentItem?.uriString ?: return
        val page = state.currentPage

        viewModelScope.launch {
            if (state.isCurrentPageBookmarked) {
                repository.removeBookmarkByPage(uri, page)
            } else {
                repository.addBookmark(uri, page, note)
            }
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            repository.removeBookmark(id)
        }
    }

    fun toggleFavorite(item: PdfItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.uriString, item.isFavorite)
        }
    }

    fun deleteRecent(item: PdfItem) {
        viewModelScope.launch {
            repository.deleteRecent(item.uriString)
        }
    }

    fun showJumpDialog(show: Boolean) {
        _readerState.update { it.copy(showJumpDialog = show) }
    }

    fun showThumbnailsSheet(show: Boolean) {
        _readerState.update { it.copy(showThumbnailsSheet = show) }
    }

    fun showBookmarksSheet(show: Boolean) {
        _readerState.update { it.copy(showBookmarksSheet = show) }
    }

    fun showDocDetailsDialog(show: Boolean) {
        _readerState.update { it.copy(showDocDetailsDialog = show) }
    }

    fun closeReader() {
        bookmarksJob?.cancel()
        pdfEngine.close()
        _readerState.value = ReaderUiState()
        // Refresh device files/recents in case progress changed
        scanDevicePdfs()
    }

    override fun onCleared() {
        super.onCleared()
        pdfEngine.close()
    }
}
