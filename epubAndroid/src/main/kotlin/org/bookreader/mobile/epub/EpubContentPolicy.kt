package org.bookreader.mobile.epub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

/** Reject unsupported active XML, then add a restrictive CSP before the SDK injects its own code. */
object EpubContentPolicy {
    const val MAX_MARKUP = 16 * 1024 * 1024
    const val CSP = "default-src 'none'; script-src https://readium/assets/readium/scripts/; " +
        "style-src 'unsafe-inline' https://readium/publication/ https://readium/assets/readium/; " +
        "img-src https://readium/publication/; font-src https://readium/publication/ https://readium/assets/readium/; " +
        "connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'"
    private val forbidden = setOf("script", "iframe", "object", "embed", "form", "input", "button", "textarea", "select",
        "audio", "video", "canvas", "foreignobject", "animate", "animatemotion", "animatetransform", "set", "base")
    private val urlAttributes = setOf("href", "src", "poster", "action", "data", "background")

    fun validateXml(bytes: ByteArray, content: Boolean, resourceKey: String = "chapter.xhtml") {
        require(bytes.size <= MAX_MARKUP) { "Markup limit" }
        var depth = 0; var nodes = 0
        val handler = object : DefaultHandler2() {
            override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw SAXException("DTD unsupported") }
            override fun resolveEntity(publicId: String?, systemId: String?): InputSource { throw SAXException("External entity blocked") }
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                require(++depth <= 64 && ++nodes <= 100_000 && attributes.length <= 128) { "XML complexity limit" }
                if (!content) return
                val tag = (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')).lowercase(Locale.ROOT)
                require(tag !in forbidden) { "Active publication content unsupported" }
                for (index in 0 until attributes.length) {
                    val name = attributes.getLocalName(index).ifEmpty { attributes.getQName(index).substringAfter(':') }.lowercase(Locale.ROOT)
                    val value = attributes.getValue(index)
                    require(!name.startsWith("on") && name !in setOf("srcdoc", "srcset", "ping") && attributes.getQName(index) != "xml:base") { "Active attribute unsupported" }
                    if (name == "http-equiv") require(value.equals("content-type", true)) { "Publication HTTP directive unsupported" }
                    if (name in urlAttributes) validateReference(value, tag == "a" && name == "href", resourceKey)
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) { depth-- }
        }
        val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
        parser.setFeature("http://xml.org/sax/features/external-general-entities", false)
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        parser.contentHandler = handler; parser.entityResolver = handler; parser.errorHandler = handler
        parser.parse(InputSource(ByteArrayInputStream(bytes)))
    }

    private fun validateReference(raw: String, externalLink: Boolean, resourceKey: String) {
        require(raw.none { it.code < 32 || it == '\\' }) { "Unsafe resource reference" }
        val uri = URI(raw.trim())
        require(!uri.isAbsolute || externalLink && uri.scheme.lowercase(Locale.ROOT) in setOf("https", "http")) { "External or active resource unsupported" }
        require(uri.rawAuthority == null || externalLink && uri.isAbsolute) { "Protocol relative resource unsupported" }
        if (!uri.isAbsolute) {
            val path = uri.path.orEmpty()
            require(!path.startsWith('/') && '%' !in path && '\\' !in path) { "Unsafe resource path" }
            val segments = resourceKey.split('/').dropLast(1).toMutableList()
            for (segment in path.split('/')) when (segment) {
                "", "." -> Unit
                ".." -> { require(segments.isNotEmpty()) { "Resource traversal unsupported" }; segments.removeAt(segments.lastIndex) }
                else -> segments += segment
            }
        }
    }

    fun parseXml(bytes: ByteArray, content: Boolean = false, resourceKey: String = "metadata.xml"): org.w3c.dom.Document {
        validateXml(bytes, content, resourceKey)
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw SAXException("External entity blocked") }
        return builder.parse(ByteArrayInputStream(bytes))
    }

    fun protectXhtml(bytes: ByteArray, resourceKey: String = "chapter.xhtml"): ByteArray {
        val document = parseXml(bytes, content = true, resourceKey)
        require(document.documentElement.localName.equals("html", true)) { "XHTML root required" }
        val heads = document.getElementsByTagNameNS("*", "head")
        require(heads.length == 1) { "XHTML head required" }
        val head = heads.item(0) as Element
        val meta = document.createElementNS(head.namespaceURI, "meta")
        meta.setAttribute("http-equiv", "Content-Security-Policy"); meta.setAttribute("content", CSP)
        head.insertBefore(meta, head.firstChild)
        val output = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
        return output.toByteArray().also { require(it.size <= MAX_MARKUP) }
    }
}
