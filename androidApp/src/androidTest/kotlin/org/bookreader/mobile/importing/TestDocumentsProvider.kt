package org.bookreader.mobile.importing

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

/** A real separately installed test APK provider. Fixtures are generated, never personal files. */
class TestDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID, DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.COLUMN_MIME_TYPES)
        return MatrixCursor(columns).apply {
            val values = mapOf(DocumentsContract.Root.COLUMN_ROOT_ID to "fixtures",
                DocumentsContract.Root.COLUMN_DOCUMENT_ID to "fixtures",
                DocumentsContract.Root.COLUMN_TITLE to "BookReader test TXT",
                DocumentsContract.Root.COLUMN_FLAGS to DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
                DocumentsContract.Root.COLUMN_MIME_TYPES to "text/plain\napplication/octet-stream")
            addRow(columns.map(values::get))
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == "fixtures" && documentId != "fixtures"

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documents(projection, listOf(documentId))

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        documents(projection, listOf("system-picker", "utf8", "unknown", "legacy", "binary"))

    private fun documents(projection: Array<out String>?, ids: List<String>): Cursor {
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS)
        return MatrixCursor(columns).apply {
            ids.forEach { id ->
                val values = mapOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID to id,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME to if (id == "system-picker") "System picker fixture.txt" else "../../$id.txt",
                    DocumentsContract.Document.COLUMN_MIME_TYPE to if (id == "fixtures") DocumentsContract.Document.MIME_TYPE_DIR else "text/plain",
                    DocumentsContract.Document.COLUMN_SIZE to if (id == "unknown" || id == "stalled") null else if (id == "oversize") MAX_TXT_BYTES + 1 else bytes(id).size.toLong(),
                    DocumentsContract.Document.COLUMN_FLAGS to 0)
                addRow(columns.map(values::get))
            }
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        require(mode == "r") // Test failures expose any accidental write to the source.
        if (documentId == "stalled") {
            val pipe = ParcelFileDescriptor.createPipe()
            Thread {
                try {
                    java.io.FileOutputStream(pipe[1].fileDescriptor).use { output ->
                        output.write("부분 본문\n".toByteArray())
                        Thread.sleep(30_000)
                    }
                } finally { pipe[1].close() }
            }.start()
            return pipe[0]
        }
        val file = File(requireNotNull(context).cacheDir, "fixture-$documentId")
        file.writeBytes(bytes(documentId))
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun bytes(id: String): ByteArray = when (id) {
        "reader-scroll", "reader-host", "reader-failure" -> (0 until 6000).joinToString("\n") { line ->
            "$id 본문 ${line.toString().padStart(5, '0')} · 읽기 위치와 줄바꿈을 검증하는 자체 생성 문장입니다."
        }.toByteArray()
        "system-picker" -> "시스템 파일 선택기로 추가한 실제 TXT입니다.\n".toByteArray()
        "empty" -> byteArrayOf()
        "legacy" -> byteArrayOf(0xb0.toByte(), 0xa1.toByte(), 0x0a)
        "binary" -> byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0, 1)
        "unknown" -> "크기를 알 수 없는 문서입니다.\n".toByteArray()
        else -> "실제 문서 제공자에서 가져온 TXT입니다.\n원본을 보존합니다.\n".toByteArray()
    }
}
