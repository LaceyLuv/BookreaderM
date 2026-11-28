package org.bookreader.mobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.R
import org.bookreader.mobile.model.Book

/** Decorative icons accompany visible labels; standalone controls supply their own description. */
@Composable
internal fun AppIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), contentDescription = description, modifier = modifier.size(24.dp))
}

@Composable
internal fun ContinueReading(book: Book, percent: Double?, onOpen: (Book) -> Unit,
    details: org.bookreader.mobile.library.BookDetails = org.bookreader.mobile.library.BookDetails()) {
    Surface(onClick = { onOpen(book) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        .testTag("continue_reading"), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookCover(book, details, Modifier.width(36.dp).height(54.dp))
            Column(Modifier.weight(1f)) {
                Text("이어읽기 · ${book.format.displayName}" + (percent?.let { " · ${it.toInt()}% 지점" } ?: ""), style = MaterialTheme.typography.labelMedium)
                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            }
            AppIcon(R.drawable.ic_arrow)
        }
    }
}

@Composable
internal fun LibraryNotice(message: String, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("import_notice")
        .semantics { liveRegion = LiveRegionMode.Polite }, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_notice")) {
                AppIcon(R.drawable.ic_close, description = "안내 닫기")
            }
        }
    }
}
