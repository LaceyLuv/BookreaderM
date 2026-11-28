package org.bookreader.mobile.library

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.*
import org.bookreader.mobile.importing.TestPickerActivity
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.ImportUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Preparation is deliberately killed by the host, never reported as a passing JUnit method. */
class LibraryBatchHostProcessTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val target get()=instrumentation.targetContext
    private val proof get()=File(target.filesDir,"library-batch-host.properties")
    private val db get()=AndroidDatabaseOwner.borrow(target)
    private fun state():org.bookreader.mobile.ui.AppUiState {
        var model:AppViewModel?=null
        compose.activityRule.scenario.onActivity { model=ViewModelProvider(it)[AppViewModel::class.java] }
        return checkNotNull(model).state.value
    }

    @Test fun prepareActiveBatchForHostKill() {
        val fixture="batch-host-${UUID.randomUUID()}"
        val manager=RoomLibraryManager(db,System::currentTimeMillis)
        val original=runBlocking(Dispatchers.IO) { manager.load().preferences }
        val monitor=object:Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                if(intent.action==Intent.ACTION_OPEN_DOCUMENT) {
                    intent.component=ComponentName(instrumentation.context,TestPickerActivity::class.java)
                    intent.putExtra("fixtures",arrayOf(fixture,"legacy"))
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithTag("add_txt").performClick()
            compose.waitUntil(20_000) { state().importing is ImportUiState.SelectEncoding }
            assertEquals(1,state().batch?.added);assertTrue(state().batch?.active==true)
            val pending=state().importing as ImportUiState.SelectEncoding
            val properties=runBlocking(Dispatchers.IO) {
                val book=db.bookDao().listBooks().single { it.originalDisplayName=="../../$fixture.txt" }
                val revision=checkNotNull(book.currentRevision)
                val shelf="host-${UUID.randomUUID()}"
                manager.createShelf(shelf,"Host generated shelf")
                manager.edit(book.id,BookEdit("Saved host title","Host author",ReadingStatus.FINISHED,setOf(shelf),CoverEdit.Generated(4)))
                manager.preferences(LibraryPreferences(LibrarySort.AUTHOR,LibraryView.LIST,LibraryFilter.FINISHED,shelf))
                db.progressDao().insertProgress(ReadingProgressEntity(book.id,revision,"future",8,"{unchanged host locator}",37.0,5,7,11))
                db.bookmarkDao().insert(BookmarkEntity("host-${book.id}",book.id,revision,"future",8,"{unchanged bookmark}","generated",12,13))
                val orphan=File(target.cacheDir,"library-cover-extract/epub-${book.id}.image")
                check(orphan.parentFile!!.mkdirs() || orphan.parentFile!!.isDirectory)
                java.io.FileOutputStream(orphan).use { output -> output.write(byteArrayOf(1,2,3));output.fd.sync() }
                Properties().apply {
                    setProperty("orphanCoverSpool",orphan.name)
                    setProperty("bookId",book.id);setProperty("sourceHash",book.sourceSha256);setProperty("revision",revision)
                    setProperty("shelfId",shelf);setProperty("pendingJob",pending.jobId)
                    setProperty("originalSort",original.sort.name);setProperty("originalView",original.view.name)
                    setProperty("originalFilter",original.filter.name);setProperty("originalShelf",original.shelfId.orEmpty())
                    setProperty("initialIds",db.bookDao().listBooks().filter { it.id!=book.id }.joinToString(",") { it.id })
                    setProperty("ready","true")
                }
            }
            java.io.FileOutputStream(proof).use { output -> properties.store(output,"Generated host checkpoint");output.fd.sync() }
            // The external script observes the durable checkpoint and kills this live instrumentation.
            // Reaching the deadline is a failed host protocol, never a successful preparation test.
            android.os.SystemClock.sleep(90_000)
            fail("Host did not force-stop the active batch")
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun coldStartKeepsCatalogAndRecordsThenResumesPendingEncoding() {
        val props=Properties().apply { proof.inputStream().use(::load) }
        compose.waitUntil(20_000) { state().importing is ImportUiState.SelectEncoding }
        assertTrue(state().notice.orEmpty().contains("완료된 책"))
        assertNull(state().batch)
        compose.onNodeWithTag("tab_LIBRARY").assertIsDisplayed()
        compose.onNodeWithTag("txt_reader").assertDoesNotExist()
        val manager=RoomLibraryManager(db,System::currentTimeMillis)
        val id=props.getProperty("bookId");val revision=props.getProperty("revision");val shelf=props.getProperty("shelfId")
        runBlocking(Dispatchers.IO) {
            val book=checkNotNull(db.bookDao().findBook(id))
            assertEquals("Saved host title",book.title);assertEquals("Host author",book.author)
            assertEquals(props.getProperty("sourceHash"),book.sourceSha256)
            val actualHash=java.security.MessageDigest.getInstance("SHA-256").digest(File(target.filesDir,"managed/${book.managedRelativePath}").readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(book.sourceSha256,actualHash)
            val catalog=manager.load()
            assertEquals(7,catalog.details[id]?.metadataEdits)
            assertEquals(ReadingStatus.FINISHED,catalog.details[id]?.readingStatus)
            assertEquals(4,catalog.details[id]?.coverPreset)
            assertEquals(setOf(shelf),catalog.memberships[id])
            assertEquals(LibraryPreferences(LibrarySort.AUTHOR,LibraryView.LIST,LibraryFilter.FINISHED,shelf),catalog.preferences)
            assertEquals(ReadingProgressEntity(id,revision,"future",8,"{unchanged host locator}",37.0,5,7,11),db.progressDao().findProgress(id,revision))
            assertEquals("{unchanged bookmark}",db.bookmarkDao().page(id,revision,Long.MAX_VALUE,"z").single().locatorJson)
        }
        assertEquals(props.getProperty("pendingJob"),(state().importing as ImportUiState.SelectEncoding).jobId)
        compose.waitUntil(10_000) { !File(target.cacheDir,"library-cover-extract/${props.getProperty("orphanCoverSpool")}").exists() }
        compose.onNodeWithTag("encoding_EUC_KR").performClick()
        compose.waitUntil(20_000) { state().importing is ImportUiState.Complete }
        assertEquals("euc-kr",(state().importing as ImportUiState.Complete).result.book.encodingId)
        compose.activityRule.scenario.close()
        runBlocking(Dispatchers.IO) {
            val originalIds=props.getProperty("initialIds").split(',').filter { it.isNotEmpty() }.toSet()
            val management=AndroidBookManagement(target)
            assertFalse(management.consumeInterruptedBatch())
            db.bookDao().listBooks().filter { it.id !in originalIds }.forEach { management.delete(it.id) }
            manager.deleteShelf(shelf)
            manager.preferences(LibraryPreferences(LibrarySort.valueOf(props.getProperty("originalSort")),
                LibraryView.valueOf(props.getProperty("originalView")),LibraryFilter.valueOf(props.getProperty("originalFilter")),
                props.getProperty("originalShelf").ifEmpty { null }))
        }
        assertTrue(proof.delete())
    }
}
