package com.venkoi.terminal.data.file

import android.content.ContentResolver
import android.net.Uri
import java.nio.charset.StandardCharsets
import javax.inject.Inject

sealed interface DocumentWriteResult {
    data object Success : DocumentWriteResult
    data class Failure(val message: String, val cause: Throwable? = null) : DocumentWriteResult
}

class SalesBatchDocumentWriter @Inject constructor(private val contentResolver: ContentResolver) {
    fun write(uri: Uri, json: String): DocumentWriteResult = try {
        val stream = contentResolver.openOutputStream(uri, "wt")
            ?: return DocumentWriteResult.Failure("Unable to open the selected document")
        stream.use {
            it.write(json.toByteArray(StandardCharsets.UTF_8))
            it.flush()
        }
        DocumentWriteResult.Success
    } catch (error: Exception) {
        DocumentWriteResult.Failure(error.message ?: "Unable to write sales export", error)
    }
}
