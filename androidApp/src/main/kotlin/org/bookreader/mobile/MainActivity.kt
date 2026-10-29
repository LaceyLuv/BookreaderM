package org.bookreader.mobile

import android.os.Bundle
import android.content.Intent
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.bookreader.mobile.ui.AndroidThemeStore
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.BookReaderApp
import org.bookreader.mobile.ui.androidLibrarySessionFactory
import org.bookreader.mobile.ui.androidReaderFactory
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.importing.DocumentIntents
import org.bookreader.mobile.importing.DocumentIntentResult

class MainActivity : FragmentActivity() {
    private lateinit var model: AppViewModel
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) handleDocument(DocumentIntents.parsePickerBatch(result.data))
    }
    private val coverPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val parsed = DocumentIntents.parsePicker(result.data)
            if (parsed is DocumentIntentResult.Accepted) model.importCover(parsed.document.uri)
            else model.rejectCover()
        }
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory get() = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AppViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return AppViewModel(
                    sessions = androidLibrarySessionFactory(applicationContext),
                    themes = AndroidThemeStore(getSharedPreferences("app_settings", MODE_PRIVATE)),
                    management = AndroidBookManagement(applicationContext),
                    readerFactory = androidReaderFactory(applicationContext),
                    epubFactory = org.bookreader.mobile.ui.androidEpubReaderFactory(applicationContext),
                    comicFactory = org.bookreader.mobile.ui.androidComicReaderFactory(applicationContext),
                    artwork = org.bookreader.mobile.library.AndroidLibraryArtwork(applicationContext),
                ) as T
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[AppViewModel::class.java]
        if (model.state.value.epubReader == null) {
            supportFragmentManager.fragments.filterIsInstance<org.bookreader.mobile.epub.EpubHostFragment>().forEach {
                supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
            }
        }
        setContent { BookReaderApp(model, onAddTxt = { picker.launch(DocumentIntents.pickerIntent()) },
            onChooseCover = { coverPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }) }) }
        if (savedInstanceState == null) handleDocument(DocumentIntents.parseExternal(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Consume this explicit request once; retained navigation owns warm restoration.
        handleDocument(DocumentIntents.parseExternal(intent))
    }

    private fun handleDocument(result: DocumentIntentResult) {
        when (result) {
            is DocumentIntentResult.Accepted -> model.receiveDocument(result.document)
            is DocumentIntentResult.AcceptedBatch -> model.receiveDocuments(result.documents)
            DocumentIntentResult.Rejected -> model.rejectDocument()
            DocumentIntentResult.Ignored -> Unit
        }
    }
}
