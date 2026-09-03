@file:OptIn(androidx.pdf.ExperimentalPdfApi::class)

package com.example.ultimapdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.pdf.Highlight
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import androidx.pdf.compose.FastScrollConfiguration
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    try {
        if (uri.scheme == "content") {
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
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewMode: PdfViewMode = PdfViewMode.NORMAL,
    pageGap: Int = 8,
    pdfViewerState: PdfViewerState = remember { PdfViewerState() },
    fabVisible: Boolean = false
) {
    val context = LocalContext.current
    val pdfUri by viewModel.pdfUri.collectAsStateWithLifecycle()
    val pdfDocument by viewModel.pdfDocument.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsStateWithLifecycle()
    val persistedPage by viewModel.persistedPage.collectAsStateWithLifecycle()
    val persistedZoom by viewModel.persistedZoom.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val isImmersive by viewModel.isImmersive.collectAsStateWithLifecycle()
    val passwordRequired by viewModel.passwordRequired.collectAsStateWithLifecycle()
    val isPasswordIncorrect by viewModel.isPasswordIncorrect.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isImmersive, pdfUri) {
        if (pdfUri == null || isImmersive) {
            controlsVisible = false
        } else {
            snapshotFlow { Pair(pdfViewerState.firstVisiblePage, pdfViewerState.firstVisiblePageOffset) }
                .collectLatest {
                    controlsVisible = true
                    delay(2000.milliseconds)
                    controlsVisible = false
                }
        }
    }

    val recentFiles by PdfDataStore.getRecentFiles(context).collectAsState(initial = emptyList())

    val currentMatchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f).toArgb()
    val otherMatchColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f).toArgb()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> viewModel.setUri(uri) }
    )

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

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
                viewModel.onPageChanged(page, zoom)
            }
    }

    val fastScrollConfig = remember {
        FastScrollConfiguration.withDrawableIdsAndDp(
            fastScrollVerticalThumbDrawableRes = R.drawable.custom_scroll_thumb,
            fastScrollPageIndicatorBackgroundDrawableRes = R.drawable.custom_indicator_background,
            fastScrollVerticalThumbMarginEnd = 2.dp,
            fastScrollPageIndicatorMarginEnd = 16.dp
        )
    }

    val hiddenFastScrollConfig = remember {
        FastScrollConfiguration.withDrawableIdsAndDp(
            fastScrollVerticalThumbDrawableRes = R.drawable.custom_scroll_thumb,
            fastScrollPageIndicatorBackgroundDrawableRes = R.drawable.custom_indicator_background,
            fastScrollVerticalThumbMarginEnd = (-100).dp,
            fastScrollPageIndicatorMarginEnd = (-100).dp
        )
    }

    var lastPage by rememberSaveable { mutableIntStateOf(0) }
    var lastOffsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pdfViewerState, pdfUri) {
        snapshotFlow { Pair(pdfViewerState.firstVisiblePage, pdfViewerState.firstVisiblePageOffset) }
            .collect { (currentPage, currentOffset) ->
//                if (currentPage == 0 && currentOffset.y <= 1f) {
//              viewModel.setTopBarVisible(true)
//                    scrollAccumulator = 0f
//                }
//                else
                if (currentPage == lastPage) {
                    val deltaY = currentOffset.y - lastOffsetY
                    
                    if ((deltaY > 0 && scrollAccumulator < 0) || (deltaY < 0 && scrollAccumulator > 0)) {
                        scrollAccumulator = 0f
                    }
                    
                    scrollAccumulator += deltaY
                    
                    if (scrollAccumulator > 25f) {
                        // Scrolling UP in PDF -> SHOW top bar
                        viewModel.setTopBarVisible(true)
                        scrollAccumulator = 0f
                    } else if (scrollAccumulator < -25f) {
                        // Scrolling DOWN in PDF -> HIDE top bar
                        viewModel.setTopBarVisible(false)
                        scrollAccumulator = 0f
                    }
                } else if (currentPage > lastPage) {
                    viewModel.setTopBarVisible(false)
                    scrollAccumulator = 0f
                } else {
                    viewModel.setTopBarVisible(true)
                    scrollAccumulator = 0f
                }
                lastPage = currentPage
                lastOffsetY = currentOffset.y
            }
    }

    LaunchedEffect(pdfDocument, isRestoring) {
        if (pdfDocument != null && isRestoring) {
            val pageToScroll = if (persistedPage == -1) 0 else persistedPage
            pdfViewerState.scrollToPage(pageToScroll)
            pdfViewerState.zoomScroll {
                zoomTo(persistedZoom)
            }
            viewModel.markRestored()
        }
    }

    LaunchedEffect(searchResults, currentMatchIndex, currentMatchColor, otherMatchColor) {
        val highlights = searchResults.flatMapIndexed { index, result ->
            val color = if (index == currentMatchIndex) currentMatchColor else otherMatchColor
            result.match.bounds.map { rect ->
                Highlight(PdfRect(result.pageNum, rect), color)
            }
        }
        pdfViewerState.setHighlights(highlights)

        if (searchResults.isNotEmpty() && currentMatchIndex in searchResults.indices) {
            val match = searchResults[currentMatchIndex]
            val firstRect = match.match.bounds.firstOrNull()
            if (firstRect != null) {
                val yOffset = (firstRect.top - 8f).coerceAtLeast(0f)
                pdfViewerState.scrollToPosition(PdfPoint(match.pageNum, firstRect.left, yOffset))
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
                    .padding(contentPadding)
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
                            val uri = remember(uriString) { uriString.toUri() }
                            val fileName = remember(uriString) { getFileName(context, uri) }
                            ElevatedCard(
                                onClick = { viewModel.setUri(uri) },
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

        if (passwordRequired) {
            PasswordDialog(
                isIncorrect = isPasswordIncorrect,
                onConfirm = { viewModel.retryWithPassword(it) },
                onDismiss = { viewModel.setUri(null) }
            )
        }

        if (pdfUri != null && pdfDocument != null) {
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
                PdfViewer(
                    modifier = Modifier
                        .onSizeChanged { viewportSize = it }
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val up = waitForUpOrCancellation()
                                if (up != null && (up.uptimeMillis - down.uptimeMillis) < 200) {
                                    if (!controlsVisible && !isImmersive) {
                                        controlsVisible = true
                                    } else {
                                        viewModel.toggleImmersive()
                                    }
                                }
                            }
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
                    fastScrollConfig = if (controlsVisible) fastScrollConfig else hiddenFastScrollConfig,
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = fabVisible && (pdfDocument?.pageCount ?: 0) > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp + contentPadding.calculateBottomPadding())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${pdfViewerState.firstVisiblePage + 1} / ${pdfDocument?.pageCount ?: 0}",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else if (pdfUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
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

@Composable
fun PasswordDialog(
    isIncorrect: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password Required") },
        text = {
            Column {
                Text("This PDF is password protected. Please enter the password to open it.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = isIncorrect,
                    supportingText = if (isIncorrect) {
                        { Text("Incorrect password. Please try again.", color = MaterialTheme.colorScheme.error) }
                    } else null
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
