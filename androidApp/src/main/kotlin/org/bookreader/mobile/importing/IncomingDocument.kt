package org.bookreader.mobile.importing

import android.content.Intent
import android.net.Uri

enum class DocumentOrigin { PICKER, VIEW, SHARE }

data class IncomingDocument(val uri: Uri, val origin: DocumentOrigin)

sealed interface DocumentIntentResult {
    data object Ignored : DocumentIntentResult
    data object Rejected : DocumentIntentResult
    data class Accepted(val document: IncomingDocument) : DocumentIntentResult
}

/** Only one explicit content URI is accepted. ClipData never chooses an alternate source. */
object DocumentIntents {
    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun parseExternal(intent: Intent): DocumentIntentResult = when (intent.action) {
        Intent.ACTION_VIEW -> parse(intent, intent.data, DocumentOrigin.VIEW)
        Intent.ACTION_SEND -> parse(intent, stream(intent), DocumentOrigin.SHARE)
        else -> DocumentIntentResult.Ignored
    }

    fun parsePicker(intent: Intent?): DocumentIntentResult =
        if (intent == null) DocumentIntentResult.Ignored else parse(intent, intent.data, DocumentOrigin.PICKER)

    @Suppress("DEPRECATION")
    private fun stream(intent: Intent): Uri? = try {
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    } catch (_: RuntimeException) { null }

    private fun parse(intent: Intent, uri: Uri?, origin: DocumentOrigin): DocumentIntentResult {
        if (uri == null || uri.scheme != "content" || uri.authority.isNullOrBlank() ||
            uri.fragment != null || uri.userInfo != null
        ) return DocumentIntentResult.Rejected
        return try {
            if (origin == DocumentOrigin.SHARE && intent.data != null && intent.data != uri) {
                return DocumentIntentResult.Rejected
            }
            intent.clipData?.let { clip ->
                if (clip.itemCount != 1) return DocumentIntentResult.Rejected
                val item = clip.getItemAt(0)
                if (item.uri != uri || item.intent != null) return DocumentIntentResult.Rejected
            }
            DocumentIntentResult.Accepted(IncomingDocument(uri, origin))
        } catch (_: RuntimeException) { DocumentIntentResult.Rejected }
    }
}
