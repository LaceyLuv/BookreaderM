package org.bookreader.mobile.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bookreader.mobile.annotation.EpubAnchor
import org.bookreader.mobile.annotation.EpubAnnotation
import org.bookreader.mobile.annotation.EpubAnnotations
import org.bookreader.mobile.annotation.HighlightColor
import org.bookreader.mobile.annotation.TxtAnnotation
import org.bookreader.mobile.annotation.TxtAnnotations
import org.bookreader.mobile.annotation.TxtQuote
import org.bookreader.mobile.importing.archiveContentRevision
import org.bookreader.mobile.importing.maxImportBytes
import org.bookreader.mobile.importing.txtContentRevision
import org.bookreader.mobile.library.LibraryFilter
import org.bookreader.mobile.library.LibrarySort
import org.bookreader.mobile.library.LibraryView
import org.bookreader.mobile.library.ReadingStatus
import org.bookreader.mobile.locator.ComicLocator
import org.bookreader.mobile.locator.EpubLocator
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.LocatorDecodeResult
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.settings.ReaderSettingsCodec
import org.bookreader.mobile.toc.TxtToc
import org.bookreader.mobile.toc.TxtTocOverride

/** Portable records. Local paths, URI grants, session counters and cached assets have no fields here. */
@Serializable
data class ReadingBackup(
    val createdAt: Long,
    val books: List<BackupBook>,
    val shelves: List<String>,
    val readerSettings: BackupSettings?,
    val librarySettings: BackupLibrarySettings?,
)

@Serializable
data class BackupBook(
    val sha256: String,
    val byteSize: Long,
    val format: String,
    val title: String,
    val displayName: String,
    val author: String?,
    val revision: String,
    val encoding: String?,
    val normalizationVersion: Int?,
    val addedAt: Long,
    val lastReadAt: Long?,
    val details: BackupBookDetails?,
    val preferences: BackupSettings?,
    val shelves: List<String>,
    val progress: List<BackupProgress>,
    val bookmarks: List<BackupBookmark>,
    val txtHighlights: List<BackupTxtHighlight>,
    val epubHighlights: List<BackupEpubHighlight>,
    val tocEdits: List<BackupTocEdit>,
)

@Serializable data class BackupBookDetails(val metadataEdits: Int, val readingStatus: String?, val coverPreset: Int)
@Serializable data class BackupSettings(val version: Int, val json: String)
@Serializable data class BackupLibrarySettings(val sort: String, val view: String, val filter: String, val shelf: String?)
@Serializable data class BackupLocation(val revision: String, val type: String, val version: Int, val json: String)
@Serializable data class BackupProgress(val location: BackupLocation, val percent: Double?, val updatedAt: Long)
@Serializable data class BackupBookmark(val id: String, val location: BackupLocation, val label: String?, val createdAt: Long, val updatedAt: Long)

@Serializable
data class BackupTxtHighlight(
    val revision: String, val id: String, val version: Int, val start: Long, val end: Long,
    val quote: String, val color: String, val note: String, val changeId: String,
)

@Serializable
data class BackupEpubHighlight(
    val revision: String, val id: String, val version: Int, val anchorJson: String,
    val locatorJson: String, val color: String, val note: String, val changeId: String,
)

@Serializable
data class BackupTocEdit(
    val revision: String, val offset: Long, val version: Int, val title: String?, val excluded: Boolean, val changeId: String,
)

enum class BackupProblem { FORMAT, VERSION, INTEGRITY, LIMIT, CHANGED_LIBRARY, DOCUMENT_IO, DISK_FULL }
class BackupFailure(val problem: BackupProblem) : Exception(problem.name)

object BackupRules {
    const val VERSION = 1
    const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    const val MAX_BOOKS = 2_000
    const val MAX_RECORDS = 20_000
    const val MAX_SHELVES = 200
    const val MAX_MEMBERSHIPS = 32
    const val MAX_LOCATOR_CHARS = 128 * 1024
    val json = Json { encodeDefaults = true }
    private val hash = Regex("[0-9a-f]{64}")
    private val token = Regex("[A-Za-z0-9_-]{1,128}")
    private val encodings = listOf("utf-8", "utf-16le", "utf-16be", "cp949", "euc-kr")
    private val locator = LocatorCodec()

    fun validate(snapshot: ReadingBackup) {
        capacity(snapshot.books.size <= MAX_BOOKS && snapshot.shelves.size <= MAX_SHELVES)
        require(snapshot.createdAt >= 0)
        val shelves = snapshot.shelves.associateBy { it.lowercase() }
        require(shelves.size == snapshot.shelves.size)
        snapshot.shelves.forEach { text(it, 80); require(it.isNotBlank() && it == it.trim()) }
        snapshot.librarySettings?.let {
            LibrarySort.valueOf(it.sort); LibraryView.valueOf(it.view); LibraryFilter.valueOf(it.filter)
            require(it.shelf == null || shelves[it.shelf.lowercase()] == it.shelf)
        }
        snapshot.readerSettings?.let { settings(it, global = true) }
        val fingerprints = HashSet<Pair<String, Long>>()
        val bookmarkIds = HashSet<String>()
        var rows = snapshot.shelves.size.toLong() + listOfNotNull(snapshot.readerSettings, snapshot.librarySettings).size
        for (book in snapshot.books) {
            rows += 1L + listOfNotNull(book.details, book.preferences).size + book.shelves.size + book.progress.size + book.bookmarks.size +
                book.txtHighlights.size + book.epubHighlights.size + book.tocEdits.size
            capacity(rows <= MAX_RECORDS)
            require(fingerprints.add(book.sha256 to book.byteSize))
            val format = BookFormat.valueOf(book.format)
            require(hash.matches(book.sha256) && book.byteSize in 0..maxImportBytes(format))
            text(book.title, 300); text(book.displayName, 256); book.author?.let { text(it, 200) }
            require(book.title.isNotBlank() && book.displayName.isNotBlank())
            require(book.addedAt >= 0 && (book.lastReadAt ?: 0) >= 0)
            val revisions = if (format == BookFormat.TXT) {
                require(book.normalizationVersion == 1 && book.encoding in encodings)
                require(book.revision == txtContentRevision(book.sha256, requireNotNull(book.encoding), 1))
                encodings.map { txtContentRevision(book.sha256, it, 1) }.toSet()
            } else {
                require(book.encoding == null && book.normalizationVersion == null)
                require(book.revision == archiveContentRevision(format, book.sha256))
                setOf(book.revision)
            }
            book.details?.let {
                require(it.metadataEdits in 0..7 && it.coverPreset in 0..5)
                it.readingStatus?.let(ReadingStatus::valueOf)
            }
            book.preferences?.let { settings(it, global = false) }
            capacity(book.shelves.size <= MAX_MEMBERSHIPS)
            require(book.shelves.toSet().size == book.shelves.size && book.shelves.all { shelves[it.lowercase()] == it })
            require(book.progress.distinctBy { it.location.revision }.size == book.progress.size)
            book.progress.forEach {
                validateLocation(it.location, book, revisions)
                require(it.updatedAt >= 0 && (it.percent == null || it.percent.isFinite() && it.percent in 0.0..100.0))
            }
            book.bookmarks.forEach {
                require(token.matches(it.id) && bookmarkIds.add(it.id) && it.createdAt in 0..<Long.MAX_VALUE && it.updatedAt >= 0)
                it.label?.let { label -> text(label, 200) }
                validateLocation(it.location, book, revisions)
            }
            validateEdits(book, format, revisions)
        }
    }

    private fun settings(value: BackupSettings, global: Boolean) {
        boundedJson(value.json, 4096)
        if (global) ReaderSettingsCodec.readGlobal(value.version, value.json)
        else ReaderSettingsCodec.readBook(value.version, value.json)
    }

    private fun validateLocation(value: BackupLocation, book: BackupBook, revisions: Set<String>) {
        require(value.revision in revisions && value.type == book.format.lowercase() && value.version == 1)
        boundedJson(value.json, MAX_LOCATOR_CHARS)
        val decoded = locator.decode(value.json) as? LocatorDecodeResult.Success ?: throw BackupFailure(BackupProblem.FORMAT)
        require(decoded.locator.contentRevision == value.revision && decoded.locator.version == value.version)
        when (val position = decoded.locator) {
            is TxtLocator -> {
                require(value.type == "txt" && position.payload.utf16Offset <= book.byteSize)
                position.payload.contextHash?.let { text(it, 128) }
            }
            is ComicLocator -> {
                require(value.type == "comic" && position.payload.sortVersion == 1)
                text(position.payload.entryKey, 4096)
            }
            is EpubLocator -> {
                require(value.type == "epub" && position.payload.sdk == "readium-kotlin" && position.payload.schemaVersion == 1)
                val sdk = position.payload.locator
                val href = requireNotNull(sdk["href"]).jsonPrimitive
                require(href.isString && href.content.isNotBlank() && !href.content.startsWith('/') && ':' !in href.content && '\\' !in href.content)
                text(href.content, 4096)
                sdk["locations"]?.jsonObject?.let { locations ->
                    for (key in listOf("progression", "totalProgression")) locations[key]?.let {
                        val number = requireNotNull(it.jsonPrimitive.doubleOrNull)
                        require(number.isFinite() && number in 0.0..1.0)
                    }
                }
            }
        }
    }

    private fun validateEdits(book: BackupBook, format: BookFormat, revisions: Set<String>) {
        require(book.txtHighlights.isEmpty() && book.tocEdits.isEmpty() || format == BookFormat.TXT)
        require(book.epubHighlights.isEmpty() || format == BookFormat.EPUB)
        checkKeys(book.txtHighlights, { it.revision }, { it.id }, TxtAnnotations.MAX_ENTRIES)
        checkKeys(book.epubHighlights, { it.revision }, { it.id }, TxtAnnotations.MAX_ENTRIES)
        checkKeys(book.tocEdits, { it.revision }, { it.offset }, TxtToc.MAX_EDITS)
        book.txtHighlights.forEach {
            require(it.revision in revisions && it.version == TxtAnnotations.VERSION)
            TxtAnnotations.validate(TxtAnnotation(it.id, TxtQuote(it.start, it.end, it.quote),
                HighlightColor.valueOf(it.color), it.note, it.changeId), book.byteSize)
        }
        book.epubHighlights.forEach {
            require(it.revision in revisions && it.version == EpubAnnotations.VERSION)
            boundedJson(it.anchorJson, EpubAnnotations.MAX_ANCHOR)
            boundedJson(it.locatorJson, EpubAnnotations.MAX_LOCATOR)
            val anchor = EpubAnnotations.json.decodeFromString<EpubAnchor>(it.anchorJson)
            EpubAnnotations.validate(EpubAnnotation(it.id, anchor, it.locatorJson, HighlightColor.valueOf(it.color), it.note, it.changeId))
        }
        book.tocEdits.forEach {
            require(it.revision in revisions && it.version == 1 && token.matches(it.changeId))
            TxtToc.validate(TxtTocOverride(it.offset, it.title, it.excluded, it.changeId), book.byteSize)
        }
    }

    private fun <T, K> checkKeys(values: List<T>, revision: (T) -> String, id: (T) -> K, perRevision: Int) {
        val groups = values.groupBy(revision)
        for (group in groups.values) {
            capacity(group.size <= perRevision)
            require(group.distinctBy(id).size == group.size)
        }
    }

    /** Limits nesting before parsing outer or embedded JSON, including arbitrary SDK locator fields. */
    fun boundedJson(value: String, maxChars: Int) {
        capacity(value.length <= maxChars)
        require(TxtAnnotations.validUnicode(value))
        val brackets = CharArray(32)
        val arraySeparators = IntArray(brackets.size)
        var depth = 0
        var inString = false
        var escaped = false
        for (c in value) {
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '[', '{' -> {
                    capacity(depth < brackets.size)
                    brackets[depth] = c
                    arraySeparators[depth++] = 0
                }
                ']', '}' -> {
                    require(depth > 0 && brackets[depth - 1] == if (c == ']') '[' else '{')
                    depth--
                }
                ',' -> if (depth > 0 && brackets[depth - 1] == '[') {
                    capacity(++arraySeparators[depth - 1] < MAX_RECORDS)
                }
            }
        }
        require(depth == 0 && !inString)
    }

    private fun text(value: String, max: Int) {
        require(value.length <= max && TxtAnnotations.validUnicode(value) && value.none { it.code < 32 || it.code == 127 })
    }

    internal fun capacity(valid: Boolean) {
        if (!valid) throw BackupFailure(BackupProblem.LIMIT)
    }
}
