package com.example.ultimapdf

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_prefs")

object PdfDataStore {
    private val VIEW_MODE = intPreferencesKey("view_mode")
    private val PAGE_GAP = intPreferencesKey("page_gap")
    private val RECENT_FILES = stringPreferencesKey("recent_files")
    private val APP_THEME = intPreferencesKey("app_theme")
    private val OCR_ENABLED = booleanPreferencesKey("experimental_ocr")

    private fun getSafeKey(uri: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(uri.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getRecentFiles(context: Context): Flow<List<String>> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val serialized = preferences[RECENT_FILES] ?: ""
                if (serialized.isEmpty()) emptyList() else serialized.split(",")
            }
    }

    suspend fun addRecentFile(context: Context, pdfUri: String) {
        context.dataStore.edit { preferences ->
            val serialized = preferences[RECENT_FILES] ?: ""
            val currentList = if (serialized.isEmpty()) mutableListOf() else serialized.split(",").toMutableList()
            
            // Remove if already exists to move it to the front
            currentList.remove(pdfUri)
            currentList.add(0, pdfUri)
            
            // Limit to 10 recent files
            val limitedList = currentList.take(10)
            val removedFiles = currentList.drop(10)
            
            preferences[RECENT_FILES] = limitedList.joinToString(",")

            // Cleanup removed files if they were internal copies
            removedFiles.forEach { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    if (uri.scheme == "file") {
                        val file = File(uri.path ?: return@forEach)
                        // Check if file is in our internal temp_pdfs directory
                        val internalDir = File(context.filesDir, "temp_pdfs")
                        if (file.absolutePath.startsWith(internalDir.absolutePath)) {
                            // Delete the file and its parent hash directory if it's empty
                            file.delete()
                            file.parentFile?.let { parent ->
                                if (parent.listFiles()?.isEmpty() == true) {
                                    parent.delete()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getViewMode(context: Context): Flow<PdfViewMode> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val index = preferences[VIEW_MODE]
                if (index != null) {
                    PdfViewMode.entries.getOrElse(index) { PdfViewMode.NORMAL }
                } else {
                    val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    if (isDarkMode) PdfViewMode.SMART_INVERT else PdfViewMode.NORMAL
                }
            }
    }

    suspend fun saveViewMode(context: Context, mode: PdfViewMode) {
        context.dataStore.edit { preferences ->
            preferences[VIEW_MODE] = mode.ordinal
        }
    }

    fun getAppTheme(context: Context): Flow<AppTheme> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val index = preferences[APP_THEME]
                if (index != null) {
                    AppTheme.entries.getOrElse(index) { AppTheme.SYSTEM_DEFAULT }
                } else {
                    AppTheme.SYSTEM_DEFAULT
                }
            }
    }

    suspend fun saveAppTheme(context: Context, theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[APP_THEME] = theme.ordinal
        }
    }

    fun getPageGap(context: Context): Flow<Int> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[PAGE_GAP] ?: 8 // Default to 8dp
            }
    }

    suspend fun savePageGap(context: Context, gap: Int) {
        context.dataStore.edit { preferences ->
            preferences[PAGE_GAP] = gap
        }
    }

    fun getOcrEnabled(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[OCR_ENABLED] ?: false
            }
    }

    suspend fun saveOcrEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OCR_ENABLED] = enabled
        }
    }

    fun getLastPage(context: Context, identifier: String): Flow<Int> {
        val key = intPreferencesKey("page_" + getSafeKey(identifier))
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(androidx.datastore.preferences.core.emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: -1
            }
    }

    suspend fun saveLastPage(context: Context, identifier: String, page: Int) {
        val key = intPreferencesKey("page_" + getSafeKey(identifier))
        context.dataStore.edit { preferences ->
            preferences[key] = page
        }
    }
}
