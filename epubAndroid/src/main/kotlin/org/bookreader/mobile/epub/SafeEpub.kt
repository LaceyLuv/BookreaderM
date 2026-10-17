package org.bookreader.mobile.epub

import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.search.searchServiceFactory
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.util.*
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.format.Format
import org.readium.r2.shared.util.format.FormatSpecification
import org.readium.r2.shared.util.format.Specification
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.*
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

/** Publication owns the virtual container; all content comes from this one validated private ZIP. */
@OptIn(ExperimentalReadiumApi::class)
object SafeEpub {
    suspend fun open(file: File): Publication {
        // withContext has prompt cancellation on return: retain ownership until delivery.
        var produced: Publication? = null
        try { return withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val deadline = System.nanoTime() + 60_000_000_000L
        fun checkpoint() { context.ensureActive(); require(System.nanoTime() < deadline) { "Publication validation time limit" } }
        val archive = BoundedZip(file, checkCancellation = ::checkpoint)
        val budget = EpubResourceBudget(archive)
        require(archive.read("mimetype", 128).toString(Charsets.US_ASCII) == "application/epub+zip") { "EPUB mimetype required" }
        require("META-INF/encryption.xml" !in archive.entries && "META-INF/rights.xml" !in archive.entries) { "Encrypted EPUB unsupported" }
        for (entry in archive.entries.values) {
            checkpoint()
            val extension = entry.key.substringAfterLast('.', "").lowercase(Locale.ROOT)
            require(extension !in setOf("js", "mjs", "wasm", "epub", "zip", "cbz")) { "Active or nested resource unsupported" }
            if (extension in setOf("xml", "opf", "ncx", "html", "xhtml", "htm", "svg")) {
                val bytes = archive.read(entry.key, EpubContentPolicy.MAX_MARKUP)
                EpubContentPolicy.validateXml(bytes, extension in setOf("html", "xhtml", "htm", "svg"), entry.key)
                if (extension in setOf("html", "xhtml", "htm"))
                    budget.protectedMarkup(entry.key, EpubContentPolicy.protectXhtml(bytes, entry.key).size)
            }
            if (extension == "css") require(entry.size <= EpubContentPolicy.MAX_MARKUP)
        }
        val allowed = EpubPackagePolicy.validate(archive)
        checkpoint()
        // A new read owner must not inherit the already-completed opening coroutine's Job.
        val container = PublicationContainer(file, allowed)
        var opened: Publication? = null
        try {
            val asset = ContainerAsset(Format(FormatSpecification(Specification.Epub, Specification.Zip), MediaType.EPUB, FileExtension("epub")), container)
            val publication = PublicationOpener(EpubParser()).open(asset, allowUserInteraction = false, onCreatePublication = {
                servicesBuilder.searchServiceFactory = { BoundedEpubSearchService(it.manifest, it.container) }
            })
                .getOrElse { throw IllegalArgumentException("EPUB parser rejected publication") }
            opened = publication
            check(publication.findService(org.readium.r2.shared.publication.services.search.SearchService::class) is BoundedEpubSearchService)
            budget.readingOrder(publication.readingOrder.map { requireNotNull(it.url().path) })
            if (publication.metadata.presentation.layout == EpubLayout.FIXED) {
                error("Fixed-layout EPUB unsupported")
            }
            require(publication.readingOrder.all { it.mediaType?.isHtml == true && it.url() !is AbsoluteUrl }) { "Unsupported EPUB reading order" }
            produced = publication
            publication
        } catch (failure: Throwable) {
            try { opened?.close() } finally { container.close() }
            throw failure
        }
        } } catch (failure: Throwable) {
            produced?.close()
            throw failure
        }
    }
}

private class PublicationContainer(file: File, private val allowed: Set<String>) : Container<Resource> {
    private val closed = AtomicBoolean(false)
    private val archive = BoundedZip(file) { check(!closed.get()) { "Publication closed" } }
    override val entries: Set<Url> = archive.entries.keys.filter { it in allowed }.map { requireNotNull(Url.fromDecodedPath(it)) }.toSet()
    override fun get(url: Url): Resource? {
        if (closed.get() || url is AbsoluteUrl || java.net.URI(url.toString()).rawQuery != null) return null
        val key = url.path ?: return null
        if (key !in allowed) return null
        val entry = archive.entries[key] ?: return null
        return InMemoryResource(null, Resource.Properties()) {
            withContext(Dispatchers.IO) {
                try {
                    val context = currentCoroutineContext()
                    context.ensureActive()
                    val extension = key.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val limit = if (extension in setOf("xml", "opf", "ncx", "html", "xhtml", "htm", "svg", "css")) EpubContentPolicy.MAX_MARKUP else 64 * 1024 * 1024
                    val bytes = archive.read(entry.key, limit, context::ensureActive)
                    Try.success(if (extension in setOf("html", "xhtml", "htm")) EpubContentPolicy.protectXhtml(bytes, key) else bytes)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Try.failure(ReadError.Decoding(failure)) }
            }
        }
    }
    override fun close() { closed.set(true) }
}
