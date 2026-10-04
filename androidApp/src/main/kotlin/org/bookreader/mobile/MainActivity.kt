package org.bookreader.mobile

import android.os.Bundle
import android.content.Intent
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
    private lateinit var model: AppViewModel
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) handleDocument(DocumentIntents.parsePicker(result.data))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AppViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return AppViewModel(
                    sessions = androidLibrarySessionFactory(applicationContext),
                    themes = AndroidThemeStore(getSharedPreferences("app_settings", MODE_PRIVATE)),
                    management = AndroidBookManagement(applicationContext),
                    readerFactory = androidReaderFactory(applicationContext),
                ) as T
            }
        }
        model = ViewModelProvider(this, factory)[AppViewModel::class.java]
        setContent { BookReaderApp(model, onAddTxt = { picker.launch(DocumentIntents.pickerIntent()) }) }
        if (savedInstanceState == null) handleDocument(DocumentIntents.parseExternal(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDocument(DocumentIntents.parseExternal(intent))
    }

    private fun handleDocument(result: DocumentIntentResult) {
        when (result) {
            is DocumentIntentResult.Accepted -> model.receiveDocument(result.document)
            DocumentIntentResult.Rejected -> model.rejectDocument()
            DocumentIntentResult.Ignored -> Unit
        }
    }
}
