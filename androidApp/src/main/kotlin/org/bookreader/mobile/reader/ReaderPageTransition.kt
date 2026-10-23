package org.bookreader.mobile.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import org.bookreader.mobile.settings.PageTransition

/** Two opaque pages move together; the publication background is never faded to a blank frame. */
@Composable
fun <T> ReaderPageTransition(value: T, transition: PageTransition, direction: Int,
    modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(value, modifier.clipToBounds(), transitionSpec = {
        if (transition == PageTransition.SLIDE) {
            (slideInHorizontally(tween(180)) { it * direction } togetherWith
                slideOutHorizontally(tween(180)) { -it * direction }).using(SizeTransform(clip = true))
        } else (EnterTransition.None togetherWith ExitTransition.None).using(null)
    }, label = "readerPageTurn") { page ->
        Box(Modifier.fillMaxSize()) { content(page) }
    }
}
