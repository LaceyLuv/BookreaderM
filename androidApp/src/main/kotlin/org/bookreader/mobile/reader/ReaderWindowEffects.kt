package org.bookreader.mobile.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.bookreader.mobile.settings.ReaderWindowOptions

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** One owner per Reader Activity/window; no global brightness or display settings are modified. */
@Composable
fun readerWindowEffects(options: ReaderWindowOptions): Boolean {
    val view = LocalView.current
    val activity = view.context.activity()
    val manager = remember(view) { view.context.getSystemService(AccessibilityManager::class.java) }
    var touchExploration by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    if (activity == null) return touchExploration
    val window = activity.window
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    val initial = rememberSaveable(saver = WindowSnapshotSaver) {
        val insets = ViewCompat.getRootWindowInsets(view)
        WindowSnapshot(window.attributes.screenBrightness,
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
            activity.requestedOrientation, controller.systemBarsBehavior,
            insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true,
            insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true)
    }
    var ownsRotation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(options, window) {
        window.attributes = window.attributes.apply { screenBrightness = options.brightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        if (options.keepScreenOn || initial.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (options.lockRotation) {
            ownsRotation = true
            if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LOCKED)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else if (ownsRotation) {
            if (activity.requestedOrientation != initial.orientation) activity.requestedOrientation = initial.orientation
            ownsRotation = false
        }
        if (options.fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = initial.barBehavior
            if (initial.statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (initial.navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    DisposableEffect(window) {
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = initial.brightness }
            if (initial.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Configuration replacement is still the same Reader. Restoring here would fight
            // the new Activity's orientation request and lose the original unlocked baseline.
            if (ownsRotation && !activity.isChangingConfigurations && activity.requestedOrientation != initial.orientation)
                activity.requestedOrientation = initial.orientation
            controller.systemBarsBehavior = initial.barBehavior
            if (initial.statusVisible) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (initial.navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    return touchExploration
}

private data class WindowSnapshot(val brightness: Float, val keepScreenOn: Boolean, val orientation: Int,
    val barBehavior: Int, val statusVisible: Boolean, val navigationVisible: Boolean)

private val WindowSnapshotSaver = listSaver<WindowSnapshot, Any>(
    save = { listOf(it.brightness, it.keepScreenOn, it.orientation, it.barBehavior, it.statusVisible, it.navigationVisible) },
    restore = { WindowSnapshot(it[0] as Float, it[1] as Boolean, it[2] as Int, it[3] as Int, it[4] as Boolean, it[5] as Boolean) },
)
