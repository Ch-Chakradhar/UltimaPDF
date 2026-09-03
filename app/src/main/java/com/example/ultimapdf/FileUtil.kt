package com.example.ultimapdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object FileUtil {

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getLong(sizeIndex)
                }
            }
        } else if (uri.scheme == "file") {
            return File(uri.path ?: return 0L).length()
        }
        return 0L
    }

    fun calculateFileHash(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyFileToInternal(context: Context, uri: Uri, hash: String, originalName: String): Uri? {
        return try {
            val tempDir = File(context.filesDir, "temp_pdfs")
            val hashDir = File(tempDir, hash)
            if (!hashDir.exists()) hashDir.mkdirs()
            
            val destFile = File(hashDir, originalName)
            if (destFile.exists()) return Uri.fromFile(destFile)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sharePdf(context: Context, uri: Uri) {
        val shareableUri: Uri = if (uri.scheme == "file") {
            val file = File(uri.path ?: return)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } else {
            uri
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, shareableUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = android.content.Intent.createChooser(intent, "Share PDF")
        context.startActivity(chooser)
    }
}
