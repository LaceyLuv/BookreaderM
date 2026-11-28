package org.bookreader.mobile.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import org.bookreader.mobile.settings.PageTransition

/** Two opaque pages move together; the publication background is never faded to a blank frame. */
@Composable
fun <T> ReaderPageTransition(value: T, transition: PageTransition, direction: Int,
    modifier: Modifier = Modifier, onSettled: (T) -> Unit = {}, content: @Composable (T) -> Unit) {
    val movement = updateTransition(value, label = "readerPageTurn")
    val settled by rememberUpdatedState(onSettled)
    LaunchedEffect(movement.currentState, movement.targetState, movement.isRunning, transition) {
        if (!movement.isRunning && movement.currentState == movement.targetState) {
            if (transition != PageTransition.NONE) withFrameNanos { }
            settled(movement.currentState)
        }
    }
    movement.AnimatedContent(modifier.clipToBounds(), transitionSpec = {
        if (transition == PageTransition.SLIDE) {
            (slideInHorizontally(tween(140, easing = FastOutSlowInEasing)) { it * direction } togetherWith
                slideOutHorizontally(tween(140, easing = FastOutSlowInEasing)) { -it * direction }).using(SizeTransform(clip = true))
        } else (EnterTransition.None togetherWith ExitTransition.None).using(null)
    }) { page ->
        Box(Modifier.fillMaxSize()) { content(page) }
    }
}
