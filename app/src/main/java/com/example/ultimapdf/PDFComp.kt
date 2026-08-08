package com.example.ultimapdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.pdf.Highlight
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.FastScrollConfiguration
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import androidx.pdf.content.PageMatchBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.util.Log
import kotlin.time.Duration.Companion.milliseconds

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    try {
        if (uri.scheme == "content") {
            // Using a cursor can sometimes throw SecurityException if permission is lost
            val cursor = try {
                context.contentResolver.query(uri, null, null, null, null)
            } catch (e: SecurityException) {
                null
            }
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = c.getString(index)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown"
}

@Composable
fun PdfThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val width = 300
                        val height = (width * page.height / page.width).coerceAtLeast(1)
                        val bitmap = createBitmap(width, height)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        bitmap
                    } else {
                        renderer.close()
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativePdfReaderScreen(
    modifier: Modifier = Modifier,
    pdfUri: Uri? = null,
    onPdfUriChange: (Uri?) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    viewMode: PdfViewMode = PdfViewMode.NORMAL,
    pageGap: Int = 8,
    searchQuery: String = "",
    matchCase: Boolean = false,
    wholeWord: Boolean = false,
    currentMatchIndex: Int = 0,
    onTotalMatchCountChange: (Int) -> Unit = {},
    onToggleImmersive: () -> Unit = {},
    onTitleChange: (String) -> Unit = {},
    pdfViewerState: PdfViewerState = remember { PdfViewerState() }
) {
    val context = LocalContext.current
    
    val recentFiles by PdfDataStore.getRecentFiles(context).collectAsState(initial = emptyList())

    LaunchedEffect(recentFiles) {
        recentFiles.forEach { uriString ->
            try {
                val uri = uriString.toUri()
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(pdfUri) {
        val name = pdfUri?.let { getFileName(context, it) } ?: "UltimaPDF"
        onTitleChange(name)
        
        pdfUri?.let { uri ->
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
            }
            PdfDataStore.addRecentFile(context, uri.toString())
        }
    }

    // 1. File picker to select a PDF
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> onPdfUriChange(uri) }
    )

    // 2. Initialize the sandboxed loader and state
    val pdfLoader = remember { SandboxedPdfLoader(context.applicationContext) }

    // Persist viewer state (page and zoom) across rotations, keyed by URI string to reset on new file
    val uriString = remember(pdfUri) { pdfUri?.toString() }
    var persistedPage by rememberSaveable(uriString) { mutableIntStateOf(-1) }
    var persistedZoom by rememberSaveable(uriString) { mutableFloatStateOf(1f) }
    var isDataStoreLoading by remember(pdfUri) { mutableStateOf(pdfUri != null) }
    var isRestoring by remember(pdfUri) { mutableStateOf(true) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Load last opened page from DataStore when URI changes
    LaunchedEffect(pdfUri) {
        pdfUri?.let { uri ->
            val savedPage = PdfDataStore.getLastPage(context, uri.toString()).first()
            if (savedPage != -1 && persistedPage == -1) {
                persistedPage = savedPage
            }
            isDataStoreLoading = false
        } ?: run { 
            isDataStoreLoading = false
            isRestoring = false
        }
    }

    LaunchedEffect(pdfViewerState, pdfUri, viewportSize) {
        snapshotFlow { 
            val centerPage = if (viewportSize.height > 0) {
                pdfViewerState.visibleOffsetToPdfPoint(
                    androidx.compose.ui.geometry.Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                )?.pageNum ?: pdfViewerState.firstVisiblePage
            } else {
                pdfViewerState.firstVisiblePage
            }
            Pair(centerPage, pdfViewerState.zoom)
        }
            .distinctUntilChanged()
            .collect { (page, zoom) ->
                if (!isRestoring) {
                    Log.d("UltimaPDF", "PDFComp: Continuous save page $page")
                    persistedPage = page
                    persistedZoom = zoom
                    pdfUri?.let { uri ->
                        PdfDataStore.saveLastPage(context, uri.toString(), page)
                    }
                }
            }
    }

    // 3. Fast scroll configuration to ensure the slider is visible and accessible
    val fastScrollConfig = remember {
        FastScrollConfiguration.withDrawableIdsAndDp(
            fastScrollVerticalThumbDrawableRes = R.drawable.custom_scroll_thumb,
            fastScrollPageIndicatorBackgroundDrawableRes = R.drawable.custom_indicator_background,
            fastScrollVerticalThumbMarginEnd = 2.dp,
            fastScrollPageIndicatorMarginEnd = 16.dp
        )
    }

    // Manual sync for TopAppBar since PdfViewer might not support nested scrolling correctly
    if (scrollBehavior != null) {
        var lastPage by rememberSaveable { mutableIntStateOf(0) }
        var lastOffsetY by rememberSaveable { mutableFloatStateOf(0f) }

        LaunchedEffect(pdfViewerState) {
            snapshotFlow { Pair(pdfViewerState.firstVisiblePage, pdfViewerState.firstVisiblePageOffset) }
                .collect { (currentPage, currentOffset) ->
                    if (currentPage == lastPage) {
                        val deltaY = currentOffset.y - lastOffsetY
                        if (deltaY != 0f) {
                            scrollBehavior.state.heightOffset = (scrollBehavior.state.heightOffset + deltaY).coerceIn(
                                scrollBehavior.state.heightOffsetLimit, 0f
                            )
                            scrollBehavior.state.contentOffset -= deltaY
                        }
                    }
                    lastPage = currentPage
                    lastOffsetY = currentOffset.y
                }
        }
    }

    // 3. Load the document asynchronously when the URI changes
    val pdfDocument by produceState<PdfDocument?>(initialValue = null, key1 = pdfUri) {
        value = pdfUri?.let { uri ->
            try {
                withContext(Dispatchers.IO) {
                    pdfLoader.openDocument(uri)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // Restore viewer state when document is loaded and DataStore has been checked
    LaunchedEffect(pdfDocument, isDataStoreLoading) {
        if (pdfDocument != null && !isDataStoreLoading) {
            if (persistedPage != -1 || persistedZoom != 1f) {
                val pageToScroll = if (persistedPage == -1) 0 else persistedPage
                pdfViewerState.scrollToPage(pageToScroll)
                pdfViewerState.zoomScroll {
                    zoomTo(persistedZoom)
                }
                // Give some time for the viewer to settle before allowing saves
                delay(200.milliseconds)
            }
            isRestoring = false
        }
    }

    data class SearchResult(val pageNum: Int, val match: PageMatchBounds)

    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }

    LaunchedEffect(pdfDocument, searchQuery, matchCase, wholeWord) {
        val doc = pdfDocument
        if (doc != null && searchQuery.length >= 3) {
            val results = doc.searchDocument(searchQuery, 0 until doc.pageCount)
            val flattenedResults = mutableListOf<SearchResult>()
            for (i in 0 until results.size()) {
                val pageNum = results.keyAt(i)
                val matches = results.valueAt(i)
                
                val pageContent = doc.getPageContent(pageNum)
                val pageText = pageContent?.textContents?.joinToString("") { it.text } ?: ""

                matches.forEach { match ->
                    val start = match.textStartIndex
                    val length = searchQuery.length
                    
                    if (start + length <= pageText.length) {
                        val textSegment = pageText.substring(start, start + length)
                        
                        val caseValid = if (matchCase) textSegment == searchQuery else true
                        val wordValid = if (wholeWord) {
                            val before = if (start > 0) pageText[start - 1] else ' '
                            val after = if (start + length < pageText.length) pageText[start + length] else ' '
                            !before.isLetterOrDigit() && !after.isLetterOrDigit()
                        } else true
                        
                        if (caseValid && wordValid) {
                            flattenedResults.add(SearchResult(pageNum, match))
                        }
                    }
                }
            }
            searchResults = flattenedResults
            onTotalMatchCountChange(flattenedResults.size)
        } else {
            searchResults = emptyList()
            onTotalMatchCountChange(0)
        }
    }

    LaunchedEffect(searchResults, currentMatchIndex) {
        val highlights = searchResults.flatMapIndexed { index, result ->
            val color =
                if (index == currentMatchIndex) Color.argb(128, 255, 165, 0) else Color.argb(
                    128,
                    255,
                    255,
                    0
                )
            result.match.bounds.map { rect ->
                Highlight(PdfRect(result.pageNum, rect), color)
            }
        }
        pdfViewerState.setHighlights(highlights)

        if (searchResults.isNotEmpty() && currentMatchIndex in searchResults.indices) {
            val match = searchResults[currentMatchIndex]
            val firstRect = match.match.bounds.firstOrNull()
            if (firstRect != null) {
                pdfViewerState.scrollToPosition(PdfPoint(match.pageNum, firstRect.left, firstRect.top))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = if (pdfDocument == null && pdfUri == null) Arrangement.Top else Arrangement.Center
    ) {
        if (pdfDocument == null && pdfUri == null){
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (recentFiles.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No recent PDFs found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Recent Files",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentFiles) { uriString ->
                            val uri = uriString.toUri()
                            val fileName = getFileName(context, uri)
                            ElevatedCard(
                                onClick = { onPdfUriChange(uri) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    PdfThumbnail(
                                        uri = uri,
                                        modifier = Modifier
                                            .height(160.dp)
                                            .fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { launcher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open New PDF")
                }
            }
        }

        // 4. Render the PDF
        if (pdfUri != null && pdfDocument != null) {
            Box(modifier = Modifier.weight(1f)) {
                PdfViewer(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onToggleImmersive() }
                            )
                        }
                        .background(
                            if (viewMode == PdfViewMode.NORMAL) 
                                MaterialTheme.colorScheme.surfaceVariant 
                            else 
                                androidx.compose.ui.graphics.Color(0xFF151515)
                        )
                        .applyPdfViewMode(viewMode),
                    pdfDocument = pdfDocument!!,
                    state = pdfViewerState,
                    verticalPageSpacing = pageGap.dp,
                    contentPadding = contentPadding,
                    fastScrollConfig = fastScrollConfig
                )

                // Floating Page Indicator
                androidx.compose.animation.AnimatedVisibility(
                    visible = (pdfDocument?.pageCount ?: 0) > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${pdfViewerState.firstVisiblePage + 1} / ${pdfDocument?.pageCount ?: 0}",
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else if (pdfUri != null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

fun Modifier.applyPdfViewMode(mode: PdfViewMode): Modifier = when (mode) {
    PdfViewMode.NORMAL -> this
    PdfViewMode.CLASSIC_INVERT -> {
        this.drawWithContent {
            val matrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    colorFilter = ColorFilter.colorMatrix(matrix)
                }
                canvas.withSaveLayer(size.toRect(), paint) {
                    drawContent()
                }
            }
        }
    }
    PdfViewMode.SMART_INVERT -> {
        this.drawWithContent {
            val matrix = ColorMatrix(
                floatArrayOf(
                    0.33f, -0.66f, -0.66f, 0f, 255f,
                    -0.66f, 0.33f, -0.66f, 0f, 255f,
                    -0.66f, -0.66f, 0.33f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    colorFilter = ColorFilter.colorMatrix(matrix)
                }
                canvas.withSaveLayer(size.toRect(), paint) {
                    drawContent()
                }
            }
        }
    }
}
