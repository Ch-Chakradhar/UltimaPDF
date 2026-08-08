package com.example.ultimapdf

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.util.Log
import androidx.pdf.compose.PdfViewerState
import com.example.ultimapdf.ui.theme.UltimaPDFTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var intentUri by mutableStateOf<Uri?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            intentUri = intent.data
        } else if (intent.action == Intent.ACTION_SEND && intent.type == "application/pdf") {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            intentUri = uri
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("saved_intent_uri", intentUri)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (savedInstanceState != null) {
            intentUri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                savedInstanceState.getParcelable("saved_intent_uri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getParcelable("saved_intent_uri")
            }
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
                            initialPdfUri = intentUri,
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
    initialPdfUri: Uri? = null,
    viewMode: PdfViewMode,
    pageGap: Int,
    onNavigateToSettings: () -> Unit
) {
    var pdfUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHandledIntentUriString by rememberSaveable { mutableStateOf<String?>(null) }

    val pdfUri = remember(pdfUriString) { pdfUriString?.toUri() }

    LaunchedEffect(initialPdfUri) {
        val initialUriString = initialPdfUri?.toString()
        if (initialUriString != null && initialUriString != lastHandledIntentUriString) {
            pdfUriString = initialUriString
            lastHandledIntentUriString = initialUriString
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var currentMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    var totalMatchCount by remember { mutableIntStateOf(0) }
    val pdfViewerState = remember { PdfViewerState() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    BackHandler(enabled = pdfUriString != null || isSearching) {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else {
            pdfUri?.let { uri ->
                val currentPage = pdfViewerState.firstVisiblePage
                scope.launch {
                    Log.d("UltimaPDF", "BackHandler: Saving page $currentPage for $uri")
                    PdfDataStore.saveLastPage(context, uri.toString(), currentPage)
                    pdfUriString = null
                }
            } ?: run {
                pdfUriString = null
            }
        }
    }

    var isImmersive by rememberSaveable { mutableStateOf(false) }
    var currentTitle by rememberSaveable { mutableStateOf("UltimaPDF") }
    
    val window = (context as? Activity)?.window
    val windowInsetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(isImmersive) {
        windowInsetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isImmersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.navigationBars())
                controller.hide(WindowInsetsCompat.Type.statusBars())
            }
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
                                        onQueryChange = { 
                                            searchQuery = it
                                            currentMatchIndex = 0
                                        },
                                        onSearch = { /* Done via onQueryChange */ },
                                        expanded = false,
                                        onExpandedChange = { },
                                        placeholder = { Text("Search PDF...") },
                                        leadingIcon = {
                                            IconButton(onClick = { 
                                                isSearching = false
                                                searchQuery = ""
                                            }) {
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
                                                    onClick = { 
                                                        if (currentMatchIndex > 0) currentMatchIndex--
                                                        else currentMatchIndex = totalMatchCount - 1
                                                    },
                                                    enabled = totalMatchCount > 0
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                                }
                                                IconButton(
                                                    onClick = { 
                                                        if (currentMatchIndex < totalMatchCount - 1) currentMatchIndex++
                                                        else currentMatchIndex = 0
                                                    },
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
                                    onClick = { matchCase = !matchCase },
                                    label = { Text("Match Case") }
                                )
                                FilterChip(
                                    selected = wholeWord,
                                    onClick = { wholeWord = !wholeWord },
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
                            if (pdfUriString != null) {
                                IconButton(onClick = {
                                    pdfUri?.let { uri ->
                                        val currentPage = pdfViewerState.firstVisiblePage
                                        scope.launch {
                                            Log.d("UltimaPDF", "TopBar: Saving page $currentPage for $uri")
                                            PdfDataStore.saveLastPage(context, uri.toString(), currentPage)
                                            pdfUriString = null
                                        }
                                    } ?: run {
                                        pdfUriString = null
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        },
                        actions = {
                            if (pdfUriString != null) {
                                IconButton(onClick = { isSearching = true }) {
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        NativePdfReaderScreen(
            pdfUri = pdfUri,
            onPdfUriChange = { pdfUriString = it?.toString() },
            modifier = Modifier.fillMaxSize(),
            contentPadding = if (isImmersive) PaddingValues(0.dp) else PaddingValues(top = innerPadding.calculateTopPadding()),
            scrollBehavior = scrollBehavior,
            viewMode = viewMode,
            pageGap = pageGap,
            searchQuery = searchQuery,
            matchCase = matchCase,
            wholeWord = wholeWord,
            currentMatchIndex = currentMatchIndex,
            onTotalMatchCountChange = { totalMatchCount = it },
            onToggleImmersive = { isImmersive = !isImmersive },
            onTitleChange = { currentTitle = it },
            pdfViewerState = pdfViewerState
        )
    }
}
