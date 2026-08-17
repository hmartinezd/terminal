package com.venkoi.terminal.data.file

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.venkoi.terminal.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Writes an immutable prepared payload to private cache and hands its content URI to Android.
 * "Shared" means the chooser was launched, not that a recipient received or processed the file.
 */
class SalesExportShareManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun share(
        json: String,
        suggestedFileName: String,
        subjectResId: Int = R.string.settings_share_subject,
        chooserTitleResId: Int = R.string.settings_share_title
    ): Boolean {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
            check(directory.isDirectory) { "Unable to create JSON share directory" }
            cleanupExpiredFiles(directory)
            // Keep each handoff immutable even when two exports receive the same human filename.
            val operationDirectory = File(directory, System.nanoTime().toString()).apply { mkdirs() }
            check(operationDirectory.isDirectory) { "Unable to create sales export share operation" }
            val file = File(operationDirectory, safeFileName(suggestedFileName))
            file.writeText(json, StandardCharsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = JSON_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(subjectResId))
            clipData = ClipData.newUri(context.contentResolver, suggestedFileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (sendIntent.resolveActivity(context.packageManager) == null) return false
        return try {
            context.startActivity(
                Intent.createChooser(sendIntent, context.getString(chooserTitleResId))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun cleanupExpiredFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MILLIS
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    private fun safeFileName(suggested: String): String {
        val name = suggested.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (name.endsWith(".json", ignoreCase = true)) name else "$name.json"
    }

    private companion object {
        const val SHARE_DIRECTORY = "json_shares"
        const val JSON_MIME_TYPE = "application/json"
        val MAX_CACHE_AGE_MILLIS = TimeUnit.DAYS.toMillis(1)
    }
}
