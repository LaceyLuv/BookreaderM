package org.bookreader.mobile.epub

import java.net.URI
import java.util.Locale
import org.w3c.dom.Element

/** Security type binding only. Readium remains the publication/TOC/Locator parser. */
object EpubPackagePolicy {
    fun validate(archive: BoundedZip): Set<String> {
        val container = EpubContentPolicy.parseXml(archive.read("META-INF/container.xml", EpubContentPolicy.MAX_MARKUP))
        val roots = container.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:container", "rootfile")
        require(roots.length in 1..16) { "EPUB package root limit" }
        val allowed = archive.entries.keys.filter { it.startsWith("META-INF/") && it.endsWith(".xml") }.toMutableSet()
        allowed += "mimetype"
        for (index in 0 until roots.length) {
            val root = roots.item(index) as Element
            val path = BoundedZip.validateKey(root.getAttribute("full-path"))
            require(path.endsWith(".opf", true) && root.getAttribute("media-type") == "application/oebps-package+xml") { "Unsupported package type binding" }
            allowed += path
            val packageXml = EpubContentPolicy.parseXml(archive.read(path, EpubContentPolicy.MAX_MARKUP))
            val items = packageXml.getElementsByTagNameNS("http://www.idpf.org/2007/opf", "item")
            require(items.length in 1..20_000) { "Package resource limit" }
            val ids = mutableSetOf<String>()
            val hrefs = mutableSetOf<String>()
            for (itemIndex in 0 until items.length) {
                val item = items.item(itemIndex) as Element
                require(item.getAttribute("id").isNotBlank() && ids.add(item.getAttribute("id"))) { "Duplicate package identifier" }
                val href = URI(item.getAttribute("href"))
                require(!href.isAbsolute && !href.path.orEmpty().startsWith('/') && href.rawAuthority == null && href.rawQuery == null && href.rawFragment == null) { "External package resource unsupported" }
                val resolved = URI("/" + path).resolve(href).normalize().path.removePrefix("/")
                BoundedZip.validateKey(resolved)
                require(resolved in archive.entries && hrefs.add(resolved)) { "Missing or duplicate package resource" }
                val extension = resolved.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val media = item.getAttribute("media-type").lowercase(Locale.ROOT)
                val extensions = when (media) {
                    "application/xhtml+xml", "text/html" -> setOf("xhtml", "html", "htm")
                    "application/x-dtbncx+xml" -> setOf("ncx")
                    "application/xml", "text/xml" -> setOf("xml")
                    "image/svg+xml" -> setOf("svg")
                    "text/css" -> setOf("css")
                    "image/png" -> setOf("png")
                    "image/jpeg" -> setOf("jpg", "jpeg")
                    "image/webp" -> setOf("webp")
                    "font/ttf", "application/x-font-ttf", "application/font-sfnt" -> setOf("ttf")
                    "font/otf", "application/vnd.ms-opentype", "application/x-font-opentype" -> setOf("otf")
                    "font/woff", "application/font-woff" -> setOf("woff")
                    "font/woff2" -> setOf("woff2")
                    else -> throw IllegalArgumentException("Unsupported publication resource type")
                }
                require(extension in extensions) { "Resource MIME and path mismatch" }
                allowed += resolved
            }
        }
        return allowed
    }
}
