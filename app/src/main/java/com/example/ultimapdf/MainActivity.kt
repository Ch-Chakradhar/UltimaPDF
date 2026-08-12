package com.example.ultimapdf

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.pdf.compose.PdfViewerState
import com.example.ultimapdf.ui.theme.UltimaPDFTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    private val viewModel: PdfViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            viewModel.setUri(intent.data)
        } else if (intent.action == Intent.ACTION_SEND && intent.type == "application/pdf") {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            viewModel.setUri(uri)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("saved_intent_uri", viewModel.pdfUri.value)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (savedInstanceState != null) {
            val intentUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                savedInstanceState.getParcelable("saved_intent_uri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable("saved_intent_uri")
            }
            viewModel.setUri(intentUri)
        } else {
            handleIntent(intent)
        }
        
        enableEdgeToEdge()

        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        setContent {
            val context = this@MainActivity
            val appTheme by PdfDataStore.getAppTheme(context).collectAsState(initial = AppTheme.SYSTEM_DEFAULT)

            val darkTheme = when (appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM_DEFAULT -> isSystemInDarkTheme()
            }

            UltimaPDFTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val context = this@MainActivity
                
                val defaultViewMode = if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                    PdfViewMode.SMART_INVERT
                } else {
                    PdfViewMode.NORMAL
                }
                
                val viewMode by PdfDataStore.getViewMode(context).collectAsState(initial = defaultViewMode)
                val pageGap by PdfDataStore.getPageGap(context).collectAsState(initial = 8)

                NavHost(
                    navController = navController, 
                    startDestination = "main",
                    enterTransition = { fadeIn(animationSpec = tween(300)) },
                    exitTransition = { fadeOut(animationSpec = tween(300)) }
                ) {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            viewMode = viewMode,
                            pageGap = pageGap,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            currentMode = viewMode,
                            onModeSelected = { mode ->
                                scope.launch {
                                    PdfDataStore.saveViewMode(context, mode)
                                }
                            },
                            currentAppTheme = appTheme,
                            onAppThemeSelected = { theme ->
                                scope.launch {
                                    PdfDataStore.saveAppTheme(context, theme)
                                }
                            },
                            currentPageGap = pageGap,
                            onPageGapChanged = { gap ->
                                scope.launch {
                                    PdfDataStore.savePageGap(context, gap)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PdfViewModel,
    viewMode: PdfViewMode,
    pageGap: Int,
    onNavigateToSettings: () -> Unit
) {
    val pdfUri by viewModel.pdfUri.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val matchCase by viewModel.matchCase.collectAsStateWithLifecycle()
    val wholeWord by viewModel.wholeWord.collectAsStateWithLifecycle()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsStateWithLifecycle()
    val totalMatchCount by viewModel.totalMatchCount.collectAsStateWithLifecycle()
    val isImmersive by viewModel.isImmersive.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pdfViewerState = remember(pdfUri) { PdfViewerState() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    BackHandler(enabled = pdfUri != null || isSearching) {
        if (isSearching) {
            viewModel.setSearching(false)
        } else {
            viewModel.setUri(null)
        }
    }

    var fabVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isSearching, pdfUri, pdfViewerState.firstVisiblePage) {
        if (pdfUri != null && !isSearching) {
            fabVisible = true
            delay(2000.milliseconds)
            fabVisible = false
        } else {
            fabVisible = false
        }
    }
    
    val window = (context as? Activity)?.window
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowInsetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(Unit) {
        windowInsetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedVisibility(
                visible = !isImmersive,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                if (isSearching) {
                    Surface(tonalElevation = 3.dp) {
                        Column {
                            SearchBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                inputField = {
                                    SearchBarDefaults.InputField(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.setSearchQuery(it) },
                                        onSearch = { /* Done via onQueryChange */ },
                                        expanded = false,
                                        onExpandedChange = { },
                                        placeholder = { Text("Search PDF...") },
                                        leadingIcon = {
                                            IconButton(onClick = { viewModel.setSearching(false) }) {
                                                Icon(Icons.Default.Close, contentDescription = "Close search")
                                            }
                                        },
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (totalMatchCount > 0) {
                                                    Text(
                                                        text = "${currentMatchIndex + 1} of $totalMatchCount",
                                                        modifier = Modifier.padding(horizontal = 8.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.prevMatch() },
                                                    enabled = totalMatchCount > 0
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                                }
                                                IconButton(
                                                    onClick = { viewModel.nextMatch() },
                                                    enabled = totalMatchCount > 0
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                                                }
                                            }
                                        }
                                    )
                                },
                                expanded = false,
                                onExpandedChange = { },
                                content = { }
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = matchCase,
                                    onClick = { viewModel.setMatchCase(!matchCase) },
                                    label = { Text("Match Case") }
                                )
                                FilterChip(
                                    selected = wholeWord,
                                    onClick = { viewModel.setWholeWord(!wholeWord) },
                                    label = { Text("Whole Word") }
                                )
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentTitle == "UltimaPDF") {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_logo),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = currentTitle,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            if (pdfUri != null) {
                                IconButton(onClick = {
                                    viewModel.setUri(null)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (pdfUri != null) {
                                IconButton(onClick = { viewModel.setSearching(true) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search")
                                }
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Screen")
                }
            }
        }
    ) { innerPadding ->
        NativePdfReaderScreen(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
            scrollBehavior = scrollBehavior,
            viewMode = viewMode,
            pageGap = pageGap,
            pdfViewerState = pdfViewerState
        )
    }
}
