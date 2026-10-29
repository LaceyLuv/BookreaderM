package org.bookreader.mobile.library

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bookreader.mobile.comic.ComicImageDecoder
import org.bookreader.mobile.comic.ComicManifest
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.epub.BoundedZip
import org.bookreader.mobile.epub.SafeEpub
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat

interface LibraryArtwork {
    suspend fun ensure(book:Book,details:BookDetails):Boolean
    suspend fun import(uri:Uri):String
}

/** Work is owned by the visible card's coroutine. At most one publication/bitmap decoder is live. */
class AndroidLibraryArtwork(context:Context):LibraryArtwork {
    private val app=context.applicationContext
    private val store=BookCoverStore.get(app)
    override suspend fun import(uri:Uri)=operations.withLock {
        withContext(Dispatchers.IO) { prune() }
        store.import(uri)
    }
    override suspend fun ensure(book:Book,details:BookDetails):Boolean=withContext(Dispatchers.IO) {
        operations.withLock {
            prune()
            val key=book.coverKey
            if(details.metadataScanned && (key==null || !key.startsWith("a-") || store.file(key)!=null))return@withLock false
            val database=AndroidDatabaseOwner.borrow(app)
            val manager=RoomLibraryManager(database,System::currentTimeMillis)
            var metadata=ExtractedBookMetadata()
            try {
                withTimeout(30_000) {
                    val root=File(app.filesDir,"managed");val source=File(root,book.managedRelativePath)
                    require(source.canonicalPath.startsWith(root.canonicalPath+File.separator) &&
                        Files.isRegularFile(source.toPath(),LinkOption.NOFOLLOW_LINKS) && source.length()==book.sourceByteSize)
                    val context=currentCoroutineContext()
                    when(book.format) {
                        BookFormat.TXT->Unit
                        BookFormat.EPUB->{
                            val publication=SafeEpub.open(source)
                            try {
                                metadata=ExtractedBookMetadata(publication.metadata.title,publication.metadata.authors.take(8).joinToString(", ") { it.name })
                                val path=publication.linkWithRel("cover")?.url()?.path
                                if(path!=null) {
                                    val staging=File(app.cacheDir,"library-cover-extract").apply { mkdirs() }
                                    val image=File.createTempFile("epub-",".image",staging)
                                    try {
                                        image.outputStream().use { BoundedZip(source,checkCancellation=context::ensureActive).copyTo(path,it,BoundedCoverImage.MAX_INPUT,context::ensureActive) }
                                        metadata=metadata.copy(coverKey=store.automatic { BoundedCoverImage.transcode(image,it) })
                                    } finally { image.delete() }
                                }
                            } finally { publication.close() }
                        }
                        BookFormat.COMIC->{
                            val manifest=ComicManifest.inspect(source,book.sourceSha256,context::ensureActive)
                            ComicImageDecoder(source,File(app.cacheDir,"library-cover-extract"),minOf(Runtime.getRuntime().maxMemory(),40L*1024*1024)).use { decoder ->
                                for(path in manifest.entryKeys.take(8)) {
                                    try {
                                        decoder.decode(path,384,576).use { image ->
                                            metadata=metadata.copy(coverKey=store.automatic { BoundedCoverImage.write(image.bitmap,it) })
                                        }
                                        break
                                    } catch(cancelled:CancellationException){throw cancelled}
                                    catch(_:Exception){context.ensureActive()}
                                }
                            }
                        }
                    }
                }
            } catch(timeout:TimeoutCancellationException){currentCoroutineContext().ensureActive()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){currentCoroutineContext().ensureActive()}
            catch(_:OutOfMemoryError){ /* A cover failure never changes the managed book or reader. */ }
            manager.applyExtracted(book.id,book.sourceSha256,metadata)
            true
        }
    }
    private suspend fun prune() {
        val now=System.currentTimeMillis()
        if(now-lastPruned<10*60*1000)return
        try {
            val keys=AndroidDatabaseOwner.borrow(app).bookDao().listBooks().mapNotNull { it.coverKey }.toSet()
            store.pruneUnusedUserCovers(keys)
            lastPruned=now
        } catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){ /* Maintenance failure must not discard a user's existing cover. */ }
    }
    companion object {
        private val operations=Mutex()
        private var lastPruned=0L
    }
}
