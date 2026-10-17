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
object SafeEpub {
    suspend fun open(file: File): Publication = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val archive = BoundedZip(file, checkCancellation = context::ensureActive)
        require(archive.read("mimetype", 128).toString(Charsets.US_ASCII) == "application/epub+zip") { "EPUB mimetype required" }
        require("META-INF/encryption.xml" !in archive.entries && "META-INF/rights.xml" !in archive.entries) { "Encrypted EPUB unsupported" }
        for (entry in archive.entries.values) {
            context.ensureActive()
            val extension = entry.key.substringAfterLast('.', "").lowercase(Locale.ROOT)
            require(extension !in setOf("js", "mjs", "wasm", "epub", "zip", "cbz")) { "Active or nested resource unsupported" }
            if (extension in setOf("xml", "opf", "ncx", "html", "xhtml", "htm", "svg")) {
                val bytes = archive.read(entry.key, EpubContentPolicy.MAX_MARKUP)
                EpubContentPolicy.validateXml(bytes, extension in setOf("html", "xhtml", "htm", "svg"), entry.key)
                if (extension in setOf("html", "xhtml", "htm")) EpubContentPolicy.protectXhtml(bytes, entry.key)
            }
            if (extension == "css") require(entry.size <= EpubContentPolicy.MAX_MARKUP)
        }
        // A new read owner must not inherit the already-completed opening coroutine's Job.
        val container = PublicationContainer(file)
        try {
            val asset = ContainerAsset(Format(FormatSpecification(Specification.Epub, Specification.Zip), MediaType.EPUB, FileExtension("epub")), container)
            val publication = PublicationOpener(EpubParser()).open(asset, allowUserInteraction = false)
                .getOrElse { throw IllegalArgumentException("EPUB parser rejected publication") }
            require(publication.readingOrder.isNotEmpty() && publication.readingOrder.size <= 20_000)
            if (publication.metadata.presentation.layout == EpubLayout.FIXED) {
                publication.close(); error("Fixed-layout EPUB unsupported")
            }
            require(publication.readingOrder.all { it.mediaType?.isHtml == true && it.url() !is AbsoluteUrl }) { "Unsupported EPUB reading order" }
            publication
        } catch (failure: Throwable) { container.close(); throw failure }
    }
}

private class PublicationContainer(file: File) : Container<Resource> {
    private val closed = AtomicBoolean(false)
    private val archive = BoundedZip(file) { check(!closed.get()) { "Publication closed" } }
    override val entries: Set<Url> = archive.entries.keys.map { requireNotNull(Url.fromDecodedPath(it)) }.toSet()
    override fun get(url: Url): Resource? {
        if (closed.get() || url is AbsoluteUrl || java.net.URI(url.toString()).rawQuery != null) return null
        val key = url.path ?: return null
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
