package org.bookreader.mobile.epub

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubPackagePolicyTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun archive(root: String = "OPS/book.opf", href: String = "chapter.xhtml", media: String = "application/xhtml+xml"): BoundedZip {
        val file = temporary.newFile()
        val entries = mapOf("mimetype" to "application/epub+zip",
            "META-INF/container.xml" to "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"$root\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
            root to "<package xmlns=\"http://www.idpf.org/2007/opf\"><manifest><item id=\"one\" href=\"$href\" media-type=\"$media\"/></manifest></package>",
            "OPS/$href" to "<html><head/><body>fixture</body></html>", "unlisted.png" to "private to archive")
        ZipOutputStream(file.outputStream()).use { out -> entries.forEach { (key, value) ->
            out.putNextEntry(ZipEntry(key));out.write(value.toByteArray());out.closeEntry()
        } }
        return BoundedZip(file)
    }
    @Test fun declaredMarkupCannotBypassValidationUsingImageOrUnknownExtension() {
        assertThrows(IllegalArgumentException::class.java) { EpubPackagePolicy.validate(archive(href = "chapter.png")) }
        assertThrows(IllegalArgumentException::class.java) { EpubPackagePolicy.validate(archive(root = "OPS/book.png")) }
        assertThrows(IllegalArgumentException::class.java) { EpubPackagePolicy.validate(archive(media = "text/javascript")) }
    }
    @Test fun onlyTypedManifestResourcesAndValidatedMetadataAreServed() {
        val allowed = EpubPackagePolicy.validate(archive())
        assertTrue("OPS/chapter.xhtml" in allowed)
        assertTrue("OPS/book.opf" in allowed)
        assertFalse("unlisted.png" in allowed)
    }
}
