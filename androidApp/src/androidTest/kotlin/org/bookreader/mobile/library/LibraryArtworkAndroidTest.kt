package org.bookreader.mobile.library

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.importing.archiveContentRevision
import org.bookreader.mobile.model.BookFormat
import org.junit.Assert.*
import org.junit.Test

/** Actual Android codec/Readium/Room; only generated fixtures and this test's UUID records are changed. */
class LibraryArtworkAndroidTest {
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun epubAndComicArtworkPreserveManagedBytesAndEveryReadingRecord() = runBlocking(Dispatchers.IO) {
        val db=AndroidDatabaseOwner.borrow(target)
        val manager=RoomLibraryManager(db,System::currentTimeMillis)
        val artwork=AndroidLibraryArtwork(target)
        for(format in listOf(BookFormat.EPUB,BookFormat.COMIC)) {
            val id=UUID.randomUUID().toString()
            val directory=File(target.filesDir,"managed/books/$id").apply { check(mkdirs()) }
            val source=File(directory,"original")
            try {
                if(format==BookFormat.EPUB) createEpubCoverFixture(source)
                else target.assets.open("comic-fixtures/product.cbz").use { input -> source.outputStream().use(input::copyTo) }
                val hash=sha(source)
                val revision=archiveContentRevision(format,hash)
                val book=BookEntity(id,format.name,"File name","fixture.${format.name.lowercase()}","books/$id/original",
                    hash,source.length(),1,2,3,currentRevision=revision,activeSessionEpoch=9)
                db.bookDao().insertBook(book)
                val progress=ReadingProgressEntity(id,revision,"future",8,"{preserve exact bytes}",27.0,9,12,19)
                val bookmark=BookmarkEntity("mark-$id",id,revision,"future",8,"{bookmark bytes}","fixture",10,11)
                db.progressDao().insertProgress(progress);db.bookmarkDao().insert(bookmark)
                assertTrue(artwork.ensure(book.toBook(),BookDetails()))
                val after=checkNotNull(db.bookDao().findBook(id))
                val key=checkNotNull(after.coverKey)
                assertTrue(key.startsWith("a-"));assertTrue(isCoverKey(key))
                val store=BookCoverStore.get(target)
                val image=checkNotNull(store.bitmap(key))
                assertTrue(image.width in 1..384 && image.height in 1..576)
                assertTrue(checkNotNull(store.file(key)).length()<=BoundedCoverImage.MAX_THUMBNAIL)
                if(format==BookFormat.EPUB) {
                    assertEquals("자체 생성 EPUB",after.title);assertEquals("Generated Author",after.author)
                }
                assertEquals(book.copy(title=after.title,author=after.author,coverKey=key,updatedAt=after.updatedAt),after)
                assertEquals(hash,sha(source));assertEquals(progress,db.progressDao().findProgress(id,revision))
                assertEquals(bookmark,db.bookmarkDao().page(id,revision,Long.MAX_VALUE,"z").single())
                val details=checkNotNull(manager.load().details[id])
                assertTrue(details.metadataScanned)
                assertFalse(artwork.ensure(after.toBook(),details))
                manager.edit(id,BookEdit("Manual title",null,ReadingStatus.FINISHED,emptySet(),CoverEdit.Generated(2)))
                artwork.ensure(after.toBook(),BookDetails()) // delayed extraction uses the current DB edit mask
                assertEquals("Manual title",db.bookDao().findBook(id)?.title)
                assertNull(db.bookDao().findBook(id)?.coverKey)
                assertEquals(progress,db.progressDao().findProgress(id,revision))
            } finally { db.bookDao().deleteBook(id);directory.deleteRecursively() }
        }
    }

    @Test fun thumbnailCodecAndCacheStayBoundedWithoutRecyclingBorrowedImages() = runBlocking(Dispatchers.IO) {
        val directory=File(target.cacheDir,"cover-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val store=BookCoverStore.get(target)
        try {
            val source=File(directory,"large.png")
            val original=Bitmap.createBitmap(2048,3072,Bitmap.Config.ARGB_8888)
            try {
                original.eraseColor(Color.rgb(11,80,50))
                source.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG,100,it)) }
            } finally { original.recycle() }
            val hash=sha(source)
            val key=store.automatic { BoundedCoverImage.transcode(source,it) }
            val borrowed=checkNotNull(store.bitmap(key))
            assertEquals(384,borrowed.width);assertEquals(576,borrowed.height)
            assertSame(borrowed,store.bitmap(key));assertEquals(hash,sha(source))
            val started=android.os.SystemClock.elapsedRealtime()
            repeat(24) { index ->
                val image=Bitmap.createBitmap(384,576,Bitmap.Config.ARGB_8888)
                try {
                    image.eraseColor(Color.rgb(30+index*7,40+index*3,90+index*4))
                    val next=store.automatic { BoundedCoverImage.write(image,it) }
                    assertNotNull(store.bitmap(next))
                    assertTrue(store.retainedBytes<=8*1024*1024)
                } finally { image.recycle() }
            }
            assertFalse("An evicted image can still be displayed by Compose",borrowed.isRecycled)
            assertNull(store.file("../../private"));assertNull(store.file("u-not-a-hash.jpg"))
            val invalid=File(directory,"invalid.png").apply { writeText("not an image, do not decode") }
            var rejected=false
            try { store.automatic { BoundedCoverImage.transcode(invalid,it) } } catch(_:IllegalArgumentException) { rejected=true }
            assertTrue(rejected)
            val args=InstrumentationRegistry.getArguments()
            if(args.getString("captureFixtureScreenshots")=="true") args.getString("additionalTestOutputDir")?.let { path ->
                File(path,"phase2-cover-cache.txt").apply { parentFile?.mkdirs() }.writeText(
                    "generatedThumbnails=24\nelapsedMs=${android.os.SystemClock.elapsedRealtime()-started}\nretainedBytes=${store.retainedBytes}\nlimitBytes=${8*1024*1024}\nborrowedBitmapPreserved=true\n")
            }
        } finally { directory.deleteRecursively() }
    }

    private fun sha(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun createEpubCoverFixture(target:File) {
        ZipInputStream(this.target.assets.open("epub-fixtures/epub3.epub")).use { input ->
            ZipOutputStream(target.outputStream()).use { output ->
                while(true) {
                    val entry=input.nextEntry?:break
                    var bytes=input.readBytes()
                    if(entry.name=="OPS/package.opf") bytes=bytes.toString(Charsets.UTF_8)
                        .replace("</metadata>","<dc:creator>Generated Author</dc:creator></metadata>")
                        .replace("id=\"image\" href=\"image.png\"", "id=\"image\" properties=\"cover-image\" href=\"image.png\"")
                        .toByteArray(Charsets.UTF_8)
                    val copied=ZipEntry(entry.name)
                    if(entry.name=="mimetype") {
                        copied.method=ZipEntry.STORED;copied.size=bytes.size.toLong();copied.compressedSize=bytes.size.toLong()
                        copied.crc=CRC32().apply { update(bytes) }.value
                    }
                    output.putNextEntry(copied);output.write(bytes);output.closeEntry()
                }
            }
        }
    }
}
