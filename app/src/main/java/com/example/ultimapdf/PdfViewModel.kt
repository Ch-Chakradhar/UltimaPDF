package com.example.ultimapdf

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.content.PageMatchBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.util.size
import kotlin.time.Duration.Companion.milliseconds

data class SearchResult(val pageNum: Int, val match: PageMatchBounds)

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val pdfLoader = SandboxedPdfLoader(context)

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    private val _pdfDocument = MutableStateFlow<PdfDocument?>(null)
    val pdfDocument: StateFlow<PdfDocument?> = _pdfDocument.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _matchCase = MutableStateFlow(false)
    val matchCase: StateFlow<Boolean> = _matchCase.asStateFlow()

    private val _wholeWord = MutableStateFlow(false)
    val wholeWord: StateFlow<Boolean> = _wholeWord.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(0)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    private val _totalMatchCount = MutableStateFlow(0)
    val totalMatchCount: StateFlow<Int> = _totalMatchCount.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isImmersive = MutableStateFlow(false)
    val isImmersive: StateFlow<Boolean> = _isImmersive.asStateFlow()

    private val _currentTitle = MutableStateFlow("UltimaPDF")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _persistedPage = MutableStateFlow(-1)
    val persistedPage: StateFlow<Int> = _persistedPage.asStateFlow()

    private val _persistedZoom = MutableStateFlow(1f)
    val persistedZoom: StateFlow<Float> = _persistedZoom.asStateFlow()

    private val _isRestoring = MutableStateFlow(true)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _passwordRequired = MutableStateFlow(false)
    val passwordRequired: StateFlow<Boolean> = _passwordRequired.asStateFlow()

    private val _isPasswordIncorrect = MutableStateFlow(false)
    val isPasswordIncorrect: StateFlow<Boolean> = _isPasswordIncorrect.asStateFlow()

    init {
        viewModelScope.launch {
            _pdfUri.collectLatest { uri ->
                if (uri != null) {
                    loadDocument(uri)
                } else {
                    _pdfDocument.value = null
                    _currentTitle.value = "UltimaPDF"
                }
            }
        }

        viewModelScope.launch {
            launch {
                _searchQuery.collectLatest { performSearch() }
            }
            launch {
                _matchCase.collectLatest { performSearch() }
            }
            launch {
                _wholeWord.collectLatest { performSearch() }
            }
        }
    }

    fun setUri(uri: Uri?) {
        if (_pdfUri.value == uri) return
        _pdfUri.value = uri
        _isRestoring.value = true
        _persistedPage.value = -1
        _persistedZoom.value = 1f
        _searchQuery.value = ""
        _isSearching.value = false
        _passwordRequired.value = false
        _isPasswordIncorrect.value = false
        
        if (uri != null) {
            viewModelScope.launch {
                val savedPage = PdfDataStore.getLastPage(context, uri.toString()).first()
                if (savedPage != -1) {
                    _persistedPage.value = savedPage
                }
            }
            
            // Try to take persistable URI permission
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {}
            
            viewModelScope.launch {
                PdfDataStore.addRecentFile(context, uri.toString())
            }
        }
    }

    private suspend fun loadDocument(uri: Uri, password: String? = null) {
        _currentTitle.value = getFileName(context, uri)
        try {
            val doc = withContext(Dispatchers.IO) {
                if (password != null) {
                    pdfLoader.openDocument(uri, password)
                } else {
                    pdfLoader.openDocument(uri)
                }
            }
            _pdfDocument.value = doc
            _passwordRequired.value = false
            _isPasswordIncorrect.value = false
        } catch (e: PdfPasswordException) {
            Log.w("PdfViewModel", "Password required or incorrect password", e)
            _passwordRequired.value = true
            _isPasswordIncorrect.value = password != null
            _pdfDocument.value = null
        } catch (e: Exception) {
            Log.e("PdfViewModel", "Error loading document", e)
            _pdfDocument.value = null
        }
    }

    fun retryWithPassword(password: String) {
        val uri = _pdfUri.value ?: return
        viewModelScope.launch {
            loadDocument(uri, password)
        }
    }

    private suspend fun performSearch() {
        val doc = _pdfDocument.value
        val query = _searchQuery.value
        if (doc != null && query.length >= 3) {
            val results = withContext(Dispatchers.IO) {
                doc.searchDocument(query, 0 until doc.pageCount)
            }
            val flattenedResults = mutableListOf<SearchResult>()
            
            // Extract text contents for case/word validation if needed
            for (i in 0 until results.size) {
                val pageNum = results.keyAt(i)
                val matches = results.valueAt(i)
                
                val pageContent = doc.getPageContent(pageNum)
                val pageText = pageContent?.textContents?.joinToString("") { it.text } ?: ""

                matches.forEach { match ->
                    val start = match.textStartIndex
                    val length = query.length
                    
                    if (start + length <= pageText.length) {
                        val textSegment = pageText.substring(start, start + length)
                        
                        val caseValid = if (_matchCase.value) textSegment == query else true
                        val wordValid = if (_wholeWord.value) {
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
            _searchResults.value = flattenedResults
            _totalMatchCount.value = flattenedResults.size
            _currentMatchIndex.value = 0
        } else {
            _searchResults.value = emptyList()
            _totalMatchCount.value = 0
        }
    }

    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
        if (!searching) {
            _searchQuery.value = ""
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMatchCase(matchCase: Boolean) {
        _matchCase.value = matchCase
    }

    fun setWholeWord(wholeWord: Boolean) {
        _wholeWord.value = wholeWord
    }

    fun nextMatch() {
        if (_totalMatchCount.value > 0) {
            _currentMatchIndex.value = (_currentMatchIndex.value + 1) % _totalMatchCount.value
        }
    }

    fun prevMatch() {
        if (_totalMatchCount.value > 0) {
            _currentMatchIndex.value = (_currentMatchIndex.value - 1 + _totalMatchCount.value) % _totalMatchCount.value
        }
    }

    fun toggleImmersive() {
        _isImmersive.value = !_isImmersive.value
    }

    fun setImmersive(immersive: Boolean) {
        _isImmersive.value = immersive
    }

    fun onPageChanged(page: Int, zoom: Float) {
        if (!_isRestoring.value) {
            _persistedPage.value = page
            _persistedZoom.value = zoom
            val uri = _pdfUri.value
            if (uri != null) {
                viewModelScope.launch {
                    PdfDataStore.saveLastPage(context, uri.toString(), page)
                }
            }
        }
    }

    fun markRestored() {
        viewModelScope.launch {
            delay(200.milliseconds) // Small delay to let viewer settle
            _isRestoring.value = false
        }
    }

    private fun getFileName(context: android.content.Context, uri: Uri): String {
        var result: String? = null
        try {
            if (uri.scheme == "content") {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) result = c.getString(index)
                    }
                }
            }
        } catch (_: Exception) {}
        
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "Unknown"
    }
}
