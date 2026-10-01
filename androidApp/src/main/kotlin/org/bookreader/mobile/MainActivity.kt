package org.bookreader.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.bookreader.mobile.ui.AndroidThemeStore
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.BookReaderApp
import org.bookreader.mobile.ui.androidLibrarySessionFactory

class MainActivity : ComponentActivity() {
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
                ) as T
            }
        }
        val model = ViewModelProvider(this, factory)[AppViewModel::class.java]
        setContent { BookReaderApp(model) }
    }
}
