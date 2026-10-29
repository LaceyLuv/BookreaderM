package org.bookreader.mobile.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.CancellationSignal
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Automatic covers are disposable; chosen covers stay in durable storage. Originals are never written. */
class BookCoverStore private constructor(context: Context) {
    private val resolver=context.applicationContext.contentResolver
    private val automatic=File(context.applicationContext.cacheDir,"library-covers")
    private val chosen=File(context.applicationContext.filesDir,"library-covers")
    private val staging=File(context.applicationContext.cacheDir,"library-cover-staging")
    private val operations=Mutex()
    private val memory=object:LruCache<String,Bitmap>(8*1024*1024) {
        override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount
        // A Compose image may still reference an evicted bitmap; never recycle a borrowed bitmap.
    }
    val retainedBytes:Int get()=memory.size()
    fun file(key:String?):File? {
        if(key==null || !isCoverKey(key))return null
        val root=if(key.startsWith("u-"))chosen else automatic
        val file=File(root,key)
        return file.takeIf { Files.isRegularFile(it.toPath(),LinkOption.NOFOLLOW_LINKS) &&
            it.length() in 12..BoundedCoverImage.MAX_THUMBNAIL.toLong() && it.canonicalFile.parentFile==root.canonicalFile }
    }
    suspend fun bitmap(key:String?):Bitmap?=withContext(Dispatchers.IO) {
        if(key==null)return@withContext null
        memory.get(key)?.let { return@withContext it }
        operations.withLock {
            memory.get(key)?.let { return@withLock it }
            val input=file(key)?:return@withLock null
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(input.path,bounds)
            if(bounds.outWidth !in 1..384 || bounds.outHeight !in 1..576)return@withLock null
            val bitmap=try { BitmapFactory.decodeFile(input.path,BitmapFactory.Options().apply { inScaled=false }) }
                catch(_:OutOfMemoryError){null}
            bitmap?.also { memory.put(key,it);if(key.startsWith("a-"))input.setLastModified(System.currentTimeMillis()) }
        }
    }

    suspend fun automatic(write:suspend (File)->Unit):String=withContext(Dispatchers.IO) {
        operations.withLock { stage(false,write) }
    }
    suspend fun import(uri:Uri):String=withContext(Dispatchers.IO) {
        operations.withLock { withTimeout(30_000) {
            require(uri.scheme=="content" && !uri.authority.isNullOrBlank() && uri.userInfo==null && uri.fragment==null) { "파일 선택기로 표지를 선택하세요." }
            check(staging.mkdirs() || staging.isDirectory)
            val source=File.createTempFile("input-",".image",staging)
            try {
                copySelected(uri,source)
                stage(true) { BoundedCoverImage.transcode(source,it) }
            } finally { source.delete() }
        } }
    }
    private suspend fun stage(user:Boolean,write:suspend (File)->Unit):String {
        val root=if(user)chosen else automatic
        check(root.mkdirs() || root.isDirectory);check(staging.mkdirs() || staging.isDirectory)
        check(StatFs(root.path).availableBytes>2L*1024*1024) { "표지를 저장할 공간이 부족합니다." }
        val temp=File.createTempFile("thumb-",".jpg",staging)
        try {
            write(temp)
            require(temp.length() in 12..BoundedCoverImage.MAX_THUMBNAIL.toLong())
            val key=(if(user)"u-" else "a-")+MessageDigest.getInstance("SHA-256").digest(temp.readBytes()).joinToString("") { "%02x".format(it) }+".jpg"
            file(key)?.let {
                if(user) check(it.setLastModified(System.currentTimeMillis())) { "표지 선택을 보존하지 못했습니다." }
                return key
            }
            val existing=root.listFiles().orEmpty().filter { isCoverKey(it.name) && it.isFile }
            var bytes=existing.sumOf { it.length() }
            if(user) check(bytes+temp.length()<=DISK_LIMIT) { "사용자 표지 보관 공간이 가득 찼습니다." }
            else for(old in existing.sortedBy { it.lastModified() }) {
                if(bytes+temp.length()<=DISK_LIMIT)break
                val size=old.length()
                if(old.delete()){ bytes-=size;memory.remove(old.name) }
            }
            check(bytes+temp.length()<=DISK_LIMIT) { "표지 캐시를 정리하지 못했습니다." }
            currentCoroutineContext().ensureActive()
            Files.move(temp.toPath(),File(root,key).toPath(),StandardCopyOption.ATOMIC_MOVE)
            val descriptor=Os.open(root.path,OsConstants.O_RDONLY,0)
            try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
            return key
        } finally { temp.delete() }
    }
    private suspend fun copySelected(uri:Uri,target:File)=coroutineScope {
        val signal=CancellationSignal()
        val watcher=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { signal.cancel() }
        }
        try {
            val asset=requireNotNull(resolver.openAssetFileDescriptor(uri,"r",signal)) { "표지 파일을 열 수 없습니다." }
            asset.use { descriptor ->
                val input=descriptor.createInputStream()
                val closer=launch(Dispatchers.IO,start=CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { runCatching { input.close() };runCatching { descriptor.close() } }
                }
                try { input.use {
                    FileOutputStream(target).use { output ->
                        val buffer=ByteArray(64*1024);var copied=0
                        while(true) {
                            ensureActive()
                            val poll=StructPollfd().apply { fd=descriptor.parcelFileDescriptor.fileDescriptor;events=OsConstants.POLLIN.toShort() }
                            if(Os.poll(arrayOf(poll),200)==0)continue
                            ensureActive();val count=input.read(buffer);if(count<0)break
                            require(count<=BoundedCoverImage.MAX_INPUT-copied) { "표지는 8MB 이하의 JPEG·PNG·WebP를 선택하세요." }
                            check(StatFs(staging.path).availableBytes>count+1024L*1024) { "표지를 저장할 공간이 부족합니다." }
                            copied+=count;output.write(buffer,0,count)
                        }
                    }
                } } finally { closer.cancel() }
            }
        } finally { watcher.cancel() }
    }

    /** Only abandoned derived/cover data. A day of grace protects an open, unsaved edit dialog. */
    suspend fun pruneUnusedUserCovers(referenced:Set<String>)=withContext(Dispatchers.IO) {
        operations.withLock {
            val cutoff=System.currentTimeMillis()-24L*60*60*1000
            chosen.listFiles().orEmpty().filter { isCoverKey(it.name) && it.name.startsWith("u-") &&
                it.name !in referenced && it.lastModified()<cutoff }.take(1000).forEach { if(it.delete())memory.remove(it.name) }
            staging.listFiles().orEmpty().filter { it.lastModified()<cutoff && it.isFile }.take(1000).forEach { it.delete() }
        }
    }
    companion object {
        private const val DISK_LIMIT=128L*1024*1024
        @Volatile private var instance:BookCoverStore?=null
        fun get(context:Context):BookCoverStore=instance?:synchronized(this){ instance?:BookCoverStore(context).also { instance=it } }
    }
}
