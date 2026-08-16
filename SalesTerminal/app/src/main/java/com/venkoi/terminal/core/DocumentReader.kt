package com.venkoi.terminal.core

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class ReadResult {
    data class Success(val content: String) : ReadResult()
    data class Failure(val message: String) : ReadResult()
}

@Singleton
class DocumentReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun readUri(uri: Uri): ReadResult {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
            if (content != null) {
                ReadResult.Success(content)
            } else {
                ReadResult.Failure("Selected file is empty or unavailable.")
            }
        } catch (e: SecurityException) {
            ReadResult.Failure("Permission denied while reading the file.")
        } catch (e: IOException) {
            ReadResult.Failure("Unable to read the selected menu file.")
        } catch (e: Exception) {
            ReadResult.Failure("An unexpected error occurred while reading the file.")
        }
    }
}
