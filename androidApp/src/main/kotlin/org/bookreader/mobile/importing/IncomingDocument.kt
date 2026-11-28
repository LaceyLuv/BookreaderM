package org.bookreader.mobile.importing

import android.content.Intent
import android.net.Uri

enum class DocumentOrigin { PICKER, VIEW, SHARE }

data class IncomingDocument(val uri: Uri, val origin: DocumentOrigin)

sealed interface DocumentIntentResult {
    data object Ignored : DocumentIntentResult
    data object Rejected : DocumentIntentResult
    data class Accepted(val document: IncomingDocument) : DocumentIntentResult
    data class AcceptedBatch(val documents: List<IncomingDocument>) : DocumentIntentResult
}

/** External intents remain single-source; our picker accepts at most 64 explicit content grants. */
object DocumentIntents {
    fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/epub+zip", "application/zip", "application/vnd.comicbook+zip", "application/x-cbz", "application/octet-stream"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    fun parseExternal(intent: Intent): DocumentIntentResult = when (intent.action) {
        Intent.ACTION_VIEW -> parse(intent, intent.data, DocumentOrigin.VIEW)
        Intent.ACTION_SEND -> parse(intent, stream(intent), DocumentOrigin.SHARE)
        else -> DocumentIntentResult.Ignored
    }

    fun parsePicker(intent: Intent?): DocumentIntentResult =
        if (intent == null) DocumentIntentResult.Ignored else parse(intent, intent.data, DocumentOrigin.PICKER)

    /** Multiple grants are accepted only from our explicit picker; external VIEW/SHARE remain single-source. */
    fun parsePickerBatch(intent: Intent?): DocumentIntentResult {
        if (intent == null) return DocumentIntentResult.Ignored
        return try {
            val clip = intent.clipData ?: return parsePicker(intent)
            if (clip.itemCount !in 1..64) return DocumentIntentResult.Rejected
            val uris = (0 until clip.itemCount).map { index ->
                val item = clip.getItemAt(index)
                val uri = item.uri ?: return DocumentIntentResult.Rejected
                if (item.intent != null || uri.scheme != "content" || uri.authority.isNullOrBlank() ||
                    uri.fragment != null || uri.userInfo != null) return DocumentIntentResult.Rejected
                uri
            }
            if (intent.data != null && intent.data != uris.first()) return DocumentIntentResult.Rejected
            val documents = uris.distinct().map { IncomingDocument(it, DocumentOrigin.PICKER) }
            if (documents.size == 1) DocumentIntentResult.Accepted(documents.single()) else DocumentIntentResult.AcceptedBatch(documents)
        } catch (_: RuntimeException) { DocumentIntentResult.Rejected }
    }

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
