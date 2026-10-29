package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TurnedInNot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.PdfEngine
import com.example.ui.PdfViewModel
import com.example.ui.ReadingTheme
import com.example.ui.ScrollViewMode
import com.example.ui.components.BookmarksSheet
import com.example.ui.components.DocumentDetailsDialog
import com.example.ui.components.JumpToPageDialog
import com.example.ui.components.PageThumbnailsSheet
import com.example.ui.components.ReadingSettingsDialog
import kotlinx.coroutines.launch

// ColorMatrix to invert RGB for night mode reading
private val NightInvertMatrix = ColorMatrix(
    floatArrayOf(
        -1.0f, 0f, 0f, 0f, 255f,
        0f, -1.0f, 0f, 0f, 255f,
        0f, 0f, -1.0f, 0f, 255f,
        0f, 0f, 0f, 1.0f, 0f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readerState by viewModel.readerState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Handle back navigation
    BackHandler {
        onBack()
    }

    var showReadingSettings by remember { mutableStateOf(false) }

    val pageBgColor = when (readerState.readingTheme) {
        ReadingTheme.DAY -> Color(0xFFEFEFF2)
        ReadingTheme.SEPIA -> Color(0xFFF1E7D0)
        ReadingTheme.NIGHT -> Color(0xFF121316)
    }

    val paperColor = when (readerState.readingTheme) {
        ReadingTheme.DAY -> Color(0xFFFFFFFF)
        ReadingTheme.SEPIA -> Color(0xFFFBF6EC)
        ReadingTheme.NIGHT -> Color(0xFF1E1F23)
    }

    val colorFilter = when (readerState.readingTheme) {
        ReadingTheme.NIGHT -> ColorFilter.colorMatrix(NightInvertMatrix)
        else -> null
    }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4.5f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBgColor)
    ) {
        if (readerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading document...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (readerState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Failed to Open PDF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = readerState.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                }
            }
        } else if (readerState.pageCount > 0) {
            val pageCount = readerState.pageCount

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                viewModel.toggleControls()
                            },
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.2f
                                }
                            }
                        )
                    }
                    .transformable(state = transformState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                if (readerState.viewMode == ScrollViewMode.CONTINUOUS) {
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = readerState.currentPage)

                    LaunchedEffect(listState) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .collect { index ->
                                viewModel.onPageChanged(index)
                            }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 70.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPageItem(
                                pageIndex = pageIndex,
                                pdfEngine = viewModel.pdfEngine,
                                colorFilter = colorFilter,
                                paperColor = paperColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // PAGER MODE
                    val pagerState = rememberPagerState(
                        initialPage = readerState.currentPage,
                        pageCount = { pageCount }
                    )

                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }
                            .collect { page ->
                                viewModel.onPageChanged(page)
                            }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 60.dp, horizontal = 10.dp)
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PdfPageItem(
                                pageIndex = pageIndex,
                                pdfEngine = viewModel.pdfEngine,
                                colorFilter = colorFilter,
                                paperColor = paperColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Top Toolbar Overlay
            AnimatedVisibility(
                visible = readerState.areControlsVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("reader_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = readerState.currentItem?.title ?: "PDF Reader",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Page ${readerState.currentPage + 1} of $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Bookmark current page
                        IconButton(
                            onClick = { viewModel.toggleBookmarkCurrentPage() },
                            modifier = Modifier.testTag("toggle_bookmark_btn")
                        ) {
                            Icon(
                                imageVector = if (readerState.isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.TurnedInNot,
                                contentDescription = "Bookmark Page",
                                tint = if (readerState.isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Reading theme & layout options
                        IconButton(
                            onClick = { showReadingSettings = true },
                            modifier = Modifier.testTag("reading_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Themes & Layout"
                            )
                        }

                        // Thumbnails grid
                        IconButton(
                            onClick = { viewModel.showThumbnailsSheet(true) },
                            modifier = Modifier.testTag("thumbnails_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "Thumbnails"
                            )
                        }

                        // Info Details
                        IconButton(
                            onClick = { viewModel.showDocDetailsDialog(true) },
                            modifier = Modifier.testTag("details_btn")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Info")
                        }
                    }
                }
            }

            // Bottom Navigation Overlay
            AnimatedVisibility(
                visible = readerState.areControlsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Slider Scrubber
                        if (pageCount > 1) {
                            Slider(
                                value = (readerState.currentPage + 1).toFloat(),
                                onValueChange = { newVal ->
                                    viewModel.onPageChanged(newVal.toInt() - 1)
                                },
                                valueRange = 1f..pageCount.toFloat(),
                                steps = if (pageCount > 2) (pageCount - 2).coerceAtMost(100) else 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .testTag("bottom_scrubber_slider")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    if (readerState.currentPage > 0) {
                                        viewModel.onPageChanged(readerState.currentPage - 1)
                                    }
                                },
                                enabled = readerState.currentPage > 0,
                                modifier = Modifier.testTag("prev_page_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Page")
                            }

                            // Tappable Page Counter pill -> Opens Jump Dialog
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures { viewModel.showJumpDialog(true) }
                                    }
                                    .testTag("page_counter_pill")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${readerState.currentPage + 1} / $pageCount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Tap to jump",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (readerState.currentPage < pageCount - 1) {
                                        viewModel.onPageChanged(readerState.currentPage + 1)
                                    }
                                },
                                enabled = readerState.currentPage < pageCount - 1,
                                modifier = Modifier.testTag("next_page_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page")
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs & Sheets
    if (showReadingSettings) {
        ReadingSettingsDialog(
            currentTheme = readerState.readingTheme,
            currentMode = readerState.viewMode,
            onSelectTheme = { viewModel.setReadingTheme(it) },
            onSelectMode = { viewModel.setViewMode(it) },
            onDismiss = { showReadingSettings = false }
        )
    }

    if (readerState.showJumpDialog) {
        JumpToPageDialog(
            currentPage = readerState.currentPage,
            pageCount = readerState.pageCount,
            onJumpToPage = { page -> viewModel.onPageChanged(page) },
            onDismiss = { viewModel.showJumpDialog(false) }
        )
    }

    if (readerState.showThumbnailsSheet) {
        PageThumbnailsSheet(
            pageCount = readerState.pageCount,
            currentPage = readerState.currentPage,
            pdfEngine = viewModel.pdfEngine,
            onSelectPage = { page -> viewModel.onPageChanged(page) },
            onDismiss = { viewModel.showThumbnailsSheet(false) }
        )
    }

    if (readerState.showBookmarksSheet) {
        BookmarksSheet(
            bookmarks = readerState.bookmarks,
            currentPage = readerState.currentPage,
            onJumpToPage = { page -> viewModel.onPageChanged(page) },
            onAddBookmark = { note -> viewModel.toggleBookmarkCurrentPage(note) },
            onDeleteBookmark = { id -> viewModel.removeBookmark(id) },
            onDismiss = { viewModel.showBookmarksSheet(false) }
        )
    }

    if (readerState.showDocDetailsDialog) {
        DocumentDetailsDialog(
            item = readerState.currentItem,
            pageCount = readerState.pageCount,
            onDismiss = { viewModel.showDocDetailsDialog(false) }
        )
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    pdfEngine: PdfEngine,
    colorFilter: ColorFilter?,
    paperColor: Color,
    modifier: Modifier = Modifier
) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }

    LaunchedEffect(pageIndex, screenWidthPx) {
        isLoading = true
        // Render at high resolution based on screen width
        pageBitmap = pdfEngine.renderPage(pageIndex, targetWidth = (screenWidthPx * 1.5f).toInt())
        isLoading = false
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = paperColor,
        shadowElevation = 3.dp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .testTag("pdf_page_$pageIndex")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = pageBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    colorFilter = colorFilter,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(paperColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        Text(
                            text = "Page ${pageIndex + 1}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
