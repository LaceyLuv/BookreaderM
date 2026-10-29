package org.bookreader.mobile.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Background callers serialize this codec. Input <=8MiB, transient bitmaps <8MiB, output <=768KiB. */
object BoundedCoverImage {
    const val MAX_INPUT = 8 * 1024 * 1024
    const val MAX_THUMBNAIL = 768 * 1024

    suspend fun transcode(source: File, target: File) {
        require(source.length() in 12..MAX_INPUT.toLong())
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(source.path,bounds)
        require(bounds.outMimeType in setOf("image/jpeg","image/png","image/webp"))
        require(bounds.outWidth in 1..1_000_000 && bounds.outHeight in 1..1_000_000)
        var sample=1
        while ((bounds.outWidth.toLong()+sample-1)/sample>768 || (bounds.outHeight.toLong()+sample-1)/sample>1152) sample*=2
        currentCoroutineContext().ensureActive()
        var owned: Bitmap?=null
        try {
            val decoded=requireNotNull(BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply {
                inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888;inScaled=false
            }))
            owned=decoded
            require(decoded.allocationByteCount<=4*1024*1024)
            val orientation=if(bounds.outMimeType=="image/jpeg") ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1) else 1
            val matrix=Matrix().apply { when(orientation) {
                2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
                5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
                7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
                else->require(orientation==1)
            } }
            if(!matrix.isIdentity) {
                val rotated=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
                if(rotated!==decoded)decoded.recycle()
                owned=rotated
            }
            currentCoroutineContext().ensureActive()
            write(requireNotNull(owned),target)
        } catch(_:OutOfMemoryError) { throw IllegalStateException("Cover image memory limit") }
        finally { owned?.recycle() }
    }

    suspend fun write(source: Bitmap, target: File) {
        currentCoroutineContext().ensureActive()
        val ratio=minOf(384.0/source.width,576.0/source.height,1.0)
        val width=(source.width*ratio).toInt().coerceAtLeast(1);val height=(source.height*ratio).toInt().coerceAtLeast(1)
        val flat=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        try {
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(source,null,Rect(0,0,width,height),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            FileOutputStream(target).use { file ->
                val limited=object:FilterOutputStream(file) {
                    var count=0
                    override fun write(b:Int) { check(++count<=MAX_THUMBNAIL);out.write(b) }
                    override fun write(b:ByteArray,off:Int,len:Int) { check(len<=MAX_THUMBNAIL-count);count+=len;out.write(b,off,len) }
                }
                check(flat.compress(Bitmap.CompressFormat.JPEG,85,limited));limited.flush();file.fd.sync()
            }
            currentCoroutineContext().ensureActive()
        } finally { flat.recycle() }
    }
}
