package org.bookreader.mobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bookreader.mobile.R
import org.bookreader.mobile.library.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.BookFormat

class LibraryActions(
    val preferences: ((LibraryPreferences) -> LibraryPreferences) -> Unit = {},
    val edit: (Book) -> Unit = {}, val save: (BookEdit) -> Unit = {}, val dismissEditor: () -> Unit = {},
    val chooseCover: () -> Unit = {}, val ensureArtwork: suspend (Book) -> Unit = {},
    val showShelves: (Boolean) -> Unit = {}, val createShelf: (String) -> Unit = {},
    val renameShelf: (String,String) -> Unit = { _,_ -> }, val deleteShelf: (String) -> Unit = {},
    val toggleSelected: (Book) -> Unit = {}, val clearSelection: () -> Unit = {},
    val classify: (String,Boolean) -> Unit = { _,_ -> },
    val dismissBatch: () -> Unit = {},
)

internal val ReadingStatus.label: String get() = when(this) {
    ReadingStatus.UNREAD -> "읽지 않음"; ReadingStatus.READING -> "읽는 중"; ReadingStatus.FINISHED -> "완독"
}
private val LibrarySort.label: String get() = when(this) {
    LibrarySort.RECENT -> "최근 읽은 순"; LibrarySort.ADDED -> "추가 날짜순"; LibrarySort.TITLE -> "제목순"; LibrarySort.AUTHOR -> "작가순"
}
private val LibraryFilter.label: String get() = if(this==LibraryFilter.ALL) "모든 상태" else ReadingStatus.valueOf(name).label

@Composable
internal fun BookCover(book: Book, details: BookDetails, modifier: Modifier = Modifier) {
    val store=BookCoverStore.get(LocalContext.current)
    val bitmap by produceState<android.graphics.Bitmap?>(null, book.coverKey) {
        value=try { store.bitmap(book.coverKey) }
        catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_:Exception){null}
    }
    val palettes=listOf(Color(0xFF265C53),Color(0xFF283F57),Color(0xFF735533),Color(0xFF67485D),Color(0xFF3A5366),Color(0xFF46574C))
    val preset=details.coverPreset
    val color=palettes[if(preset==0) (book.title.hashCode().toLong().and(0x7fffffff)%palettes.size).toInt() else preset.coerceIn(0,5)]
    BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).background(color).clearAndSetSemantics { }) {
        val rendered=bitmap
        if(rendered!=null) Image(rendered.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            // Cover lettering is decorative artwork. The adjacent caption provides the full
            // accessible title/author and continues to respect the user's system font scale.
            val compact=maxWidth<100.dp
            val tiny=maxWidth<56.dp
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1f)) {
            Box(Modifier.fillMaxHeight().width(if(preset%2==0)8.dp else 3.dp).background(Color.White.copy(alpha=.18f)))
            if(tiny) Text(book.format.displayName.take(1),Modifier.align(Alignment.Center),color=Color.White,
                style=MaterialTheme.typography.titleSmall,maxLines=1)
            else Column(Modifier.fillMaxSize().padding(if(compact)8.dp else 14.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Text(book.format.displayName,color=Color.White.copy(alpha=.75f),maxLines=1,
                    style=MaterialTheme.typography.labelSmall.let { if(compact)it.copy(fontSize=9.sp,lineHeight=11.sp) else it })
                Column(verticalArrangement=Arrangement.spacedBy(if(compact)4.dp else 8.dp)) {
                    if(preset%3==0) HorizontalDivider(Modifier.width(24.dp),color=Color.White.copy(alpha=.65f))
                    Text(book.title,color=Color.White,style=MaterialTheme.typography.titleMedium.let {
                        if(compact)it.copy(fontSize=12.sp,lineHeight=15.sp) else it },fontWeight=FontWeight.SemiBold,
                        maxLines=if(compact)3 else 5,overflow=TextOverflow.Ellipsis)
                    book.author?.let { Text(it,color=Color.White.copy(alpha=.85f),
                        style=MaterialTheme.typography.labelSmall.let { style -> if(compact)style.copy(fontSize=9.sp,lineHeight=11.sp) else style },
                        maxLines=if(compact)1 else 2,overflow=TextOverflow.Ellipsis) }
                }
                if(preset%3==1) HorizontalDivider(color=Color.White.copy(alpha=.35f)) else Spacer(Modifier.height(4.dp))
            }
            }
        }
    }
}

/**
 * Dedicated bottom dock for resuming the last committed reading position.
 * Preserves testTag("continue_reading") and single-tap onOpenBook(book).
 */
@Composable
internal fun ContinueReadingDock(
    book: Book,
    percent: Double?,
    onOpenBook: (Book) -> Unit,
    details: BookDetails,
    modifier: Modifier = Modifier,
) {
    val progressLabel = percent?.let { "${it.toInt().coerceIn(0, 100)}%" } ?: "진행률 확인 필요"
    val progressFraction = percent?.div(100.0)?.toFloat()?.coerceIn(0f, 1f)

    Surface(
        onClick = { onOpenBook(book) },
        modifier = modifier
            .fillMaxWidth()
            .testTag("continue_reading"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(book, details, Modifier.width(32.dp).height(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "이어읽기 · ${book.format.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = LibraryIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}

/**
 * The format filter is local. The layout switches use the saved library preference.
 */
@Composable
internal fun LibraryToolbarRow(
    selectedFormat: BookFormat?,
    isListLayout: Boolean,
    onSelectFormat: (BookFormat?) -> Unit,
    onToggleLayout: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormatFilterSegment(
                    label = "전체",
                    selected = selectedFormat == null,
                    onClick = { onSelectFormat(null) },
                )
                BookFormat.entries.forEach { format ->
                    FormatFilterSegment(
                        label = format.displayName,
                        selected = selectedFormat == format,
                        onClick = { onSelectFormat(format) },
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayoutToggleSegment(
                    contentDescription = "그리드 보기",
                    selected = !isListLayout,
                    onClick = { onToggleLayout(false) },
                ) {
                    Icon(
                        imageVector = LibraryIcons.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                LayoutToggleSegment(
                    contentDescription = "리스트 보기",
                    selected = isListLayout,
                    onClick = { onToggleLayout(true) },
                ) {
                    Icon(
                        imageVector = LibraryIcons.ListView,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatFilterSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .semantics {
                this.selected = selected
                this.role = Role.Tab
            },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LayoutToggleSegment(
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = (if (selected) Modifier else Modifier.testTag("library_view"))
            .size(40.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
                this.role = Role.Tab
            },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
internal fun LibraryBookCard(
    book: Book,
    catalog: LibraryCatalog,
    selected: Boolean,
    selecting: Boolean,
    wide: Boolean,
    dark: Boolean,
    actions: LibraryActions,
    onInfo: (Book) -> Unit,
    onOpen: (Book) -> Unit,
) {
    LaunchedEffect(book.id, book.coverKey) {
        if (book.availability == BookAvailability.READY) actions.ensureArtwork(book)
    }
    val details = catalog.details[book.id] ?: BookDetails()
    val shape = RoundedCornerShape(16.dp)
    val spine = bookSpineColors(book.format, dark)
    val open = { onOpen(book) }
    val select = { actions.toggleSelected(book) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("book_${book.id}").clip(shape)
            .combinedClickable(
                onClick = { if (selecting) select() else open() },
                onLongClick = select,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BookCover(book, details, Modifier.width(42.dp).height(63.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LibraryCardCaption(book, catalog)
                }
                Column(horizontalAlignment = Alignment.End) {
                    LibraryCardOpenButton(book, selected, selecting, select, open, Modifier)
                    LibraryCardInfoButton(book, onInfo)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().background(spine.band).heightIn(min = 64.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(book, details, Modifier.width(34.dp).height(51.dp))
                    Text(
                        book.format.displayName,
                        color = spine.label,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LibraryCardCaption(book, catalog)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryCardOpenButton(book, selected, selecting, select, open, Modifier.weight(1f))
                    LibraryCardInfoButton(book, onInfo)
                }
            }
        }
    }
}

@Composable
private fun LibraryCardCaption(book: Book, catalog: LibraryCatalog) {
    Text(
        book.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        book.author ?: book.originalDisplayName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (book.author == null) Modifier else Modifier.testTag("book_author_${book.id}"),
    )
    val progress = catalog.progress[book.id]
    Text(
        catalog.status(book).label + (progress?.let { " · ${it.toInt()}%" } ?: ""),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("reading_status_${book.id}"),
    )
    if (progress != null) {
        LinearProgressIndicator(progress = { (progress / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
    }
    if (book.availability != BookAvailability.READY) {
        Text(
            book.availability.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("availability_${book.id}"),
        )
    }
    Text(
        formatReadableByteSize(book.sourceByteSize),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LibraryCardOpenButton(
    book: Book,
    selected: Boolean,
    selecting: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    if (selecting) {
        Checkbox(selected, { onSelect() }, modifier.testTag("select_${book.id}"))
    } else {
        TextButton(onClick = onOpen, modifier = modifier.heightIn(min = 44.dp).testTag("read_${book.id}")) {
            Text(if (book.availability == BookAvailability.READY) "읽기" else "확인", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LibraryCardInfoButton(book: Book, onInfo: (Book) -> Unit) {
    IconButton(onClick = { onInfo(book) }, modifier = Modifier.testTag("info_${book.id}")) {
        AppIcon(R.drawable.ic_info, description = "책 정보")
    }
}

@Composable
internal fun LibraryControls(state: AppUiState,actions: LibraryActions) {
    var menu by remember { mutableStateOf<String?>(null) }
    var classify by remember { mutableStateOf(false) }
    val preferences=state.catalog.preferences
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box {
            TextButton(onClick={menu="sort"},modifier=Modifier.testTag("library_sort")) { Text(preferences.sort.label+" ▾") }
            DropdownMenu(expanded=menu=="sort",onDismissRequest={menu=null}) { LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(text={Text(sort.label)},onClick={actions.preferences { it.copy(sort=sort) };menu=null},modifier=Modifier.testTag("sort_${sort.name}"))
            } }
        }
        Box {
            TextButton(onClick={menu="filter"},modifier=Modifier.testTag("library_filter")) { Text(preferences.filter.label+" ▾") }
            DropdownMenu(expanded=menu=="filter",onDismissRequest={menu=null}) { LibraryFilter.entries.forEach { filter ->
                DropdownMenuItem(text={Text(filter.label)},onClick={actions.preferences { it.copy(filter=filter) };menu=null},modifier=Modifier.testTag("filter_${filter.name}"))
            } }
        }
        Box {
            TextButton(onClick={menu="shelf"},modifier=Modifier.testTag("library_shelf_filter")) {
                Text((state.catalog.shelves.firstOrNull { it.id==preferences.shelfId }?.name?:"모든 책장")+" ▾",maxLines=1)
            }
            DropdownMenu(expanded=menu=="shelf",onDismissRequest={menu=null}) {
                DropdownMenuItem(text={Text("모든 책장")},onClick={actions.preferences { it.copy(shelfId=null) };menu=null})
                state.catalog.shelves.forEach { shelf -> DropdownMenuItem(text={Text(shelf.name)},onClick={actions.preferences { it.copy(shelfId=shelf.id) };menu=null}) }
            }
        }
        TextButton(onClick={actions.showShelves(true)},modifier=Modifier.testTag("manage_shelves")) { Text("책장 관리") }
    }
    if(state.selectedBooks.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text("${state.selectedBooks.size}권 선택",Modifier.padding(12.dp),style=MaterialTheme.typography.labelLarge)
        TextButton(onClick={classify=true},modifier=Modifier.testTag("classify_books")) { Text("책장에 분류") }
        TextButton(onClick=actions.clearSelection,modifier=Modifier.testTag("clear_selection")) { Text("선택 해제") }
    }
    if(classify) AlertDialog(onDismissRequest={classify=false},title={Text("선택한 책 분류")},text={
        Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())) {
            if(state.catalog.shelves.isEmpty()) Text("책장 관리에서 먼저 책장을 만들어 주세요.")
            state.catalog.shelves.forEach { shelf ->
                Text(shelf.name,style=MaterialTheme.typography.titleSmall)
                Row { TextButton(onClick={actions.classify(shelf.id,true);classify=false},modifier=Modifier.testTag("classify_add_${shelf.id}")) { Text("추가") }
                    TextButton(onClick={actions.classify(shelf.id,false);classify=false}) { Text("빼기") } }
            }
        }
    },confirmButton={TextButton(onClick={classify=false}){Text("닫기")}})
}

@Composable
internal fun BookEditor(state: AppUiState,actions: LibraryActions) {
    val book=state.editingBook?:return
    key(book.id) {
        val details=state.catalog.details[book.id]?:BookDetails()
        var title by remember { mutableStateOf(book.title) }
        var author by remember { mutableStateOf(book.author.orEmpty()) }
        var status by remember { mutableStateOf(details.readingStatus) }
        var shelves by remember { mutableStateOf(state.catalog.memberships[book.id].orEmpty()) }
        var cover by remember { mutableStateOf<CoverEdit>(CoverEdit.Keep) }
        // StateFlow can conflate null -> the same cached key between two frames. A successful
        // selection has its own version so reselecting an image still overrides a preset.
        LaunchedEffect(state.pendingCoverKey,state.coverSelectionVersion) { state.pendingCoverKey?.let { cover=CoverEdit.Image(it) } }
        val busy=state.editorSaving||state.coverLoading
        AlertDialog(onDismissRequest=actions.dismissEditor,title={Text("책 정보 편집")},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title,{title=it.take(300)},label={Text("제목")},singleLine=true,enabled=!busy,modifier=Modifier.testTag("edit_book_title"))
                OutlinedTextField(author,{author=it.take(200)},label={Text("작가")},singleLine=true,enabled=!busy,modifier=Modifier.testTag("edit_book_author"))
                Text("읽기 상태",style=MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(status==null,{status=null},label={Text("자동")},enabled=!busy)
                    ReadingStatus.entries.forEach { choice -> FilterChip(status==choice,{status=choice},label={Text(choice.label)},enabled=!busy,modifier=Modifier.testTag("edit_status_${choice.name}")) }
                }
                Text("표지",style=MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    val previewKey=when(val choice=cover) { CoverEdit.Keep->book.coverKey;is CoverEdit.Generated->null;is CoverEdit.Image->choice.key }
                    BookCover(book.copy(title=title.ifBlank { book.title },author=author.takeIf { it.isNotBlank() },coverKey=previewKey),
                        details.copy(coverPreset=(cover as? CoverEdit.Generated)?.preset?:details.coverPreset),Modifier.width(80.dp).height(120.dp))
                    Column { TextButton(onClick=actions.chooseCover,enabled=!busy,modifier=Modifier.testTag("choose_book_cover")) { Text("이미지 선택") }
                        if(state.coverLoading) CircularProgressIndicator(Modifier.size(24.dp)) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("자동","남색","모래","자주","회청","숲").forEachIndexed { index,label ->
                        FilterChip((cover as? CoverEdit.Generated)?.preset==index,{cover=CoverEdit.Generated(index)},label={Text(label)},enabled=!busy,
                            modifier=Modifier.testTag("cover_preset_$index"))
                    }
                }
                Text("책장",style=MaterialTheme.typography.titleSmall)
                if(state.catalog.shelves.isEmpty()) Text("책장 관리에서 분류할 책장을 만들 수 있어요.",style=MaterialTheme.typography.bodySmall)
                state.catalog.shelves.forEach { shelf -> Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(shelf.id in shelves,{checked->shelves=if(checked)shelves+shelf.id else shelves-shelf.id},enabled=!busy)
                    Text(shelf.name)
                } }
                Text("수정한 정보는 자동 추출이 덮어쓰지 않습니다. 원본 책과 독서 위치는 그대로 유지됩니다.",style=MaterialTheme.typography.bodySmall)
                state.editorError?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("book_edit_error")) }
            }
        },confirmButton={ TextButton(onClick={actions.save(BookEdit(title,author,status,shelves,cover))},enabled=!busy&&title.isNotBlank(),
            modifier=Modifier.testTag("save_book_edit")) { Text(if(state.editorSaving)"저장 중" else "저장") } },
            dismissButton={TextButton(onClick=actions.dismissEditor,enabled=!state.editorSaving){Text("취소")}})
    }
}

@Composable
internal fun ShelfManager(state: AppUiState,actions: LibraryActions) {
    if(!state.shelvesVisible)return
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Shelf?>(null) }
    var removing by remember { mutableStateOf<Shelf?>(null) }
    AlertDialog(onDismissRequest={actions.showShelves(false)},title={Text("책장 관리")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name,{name=it.take(80)},label={Text("새 책장 이름")},singleLine=true,modifier=Modifier.testTag("new_shelf_name"))
            TextButton(onClick={actions.createShelf(name);name=""},enabled=name.isNotBlank()&&state.catalog.shelves.none { it.name.lowercase()==name.trim().lowercase() },
                modifier=Modifier.testTag("create_shelf")) { Text("책장 만들기") }
            state.catalog.shelves.forEach { shelf ->
                Text(shelf.name,style=MaterialTheme.typography.titleMedium)
                Row { TextButton(onClick={editing=shelf}) { Text("이름 변경") };TextButton(onClick={removing=shelf}) { Text("책장 삭제") } }
            }
            state.notice?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={TextButton(onClick={actions.showShelves(false)}){Text("닫기")}})
    editing?.let { shelf ->
        var renamed by remember(shelf.id) { mutableStateOf(shelf.name) }
        AlertDialog(onDismissRequest={editing=null},title={Text("책장 이름 변경")},text={OutlinedTextField(renamed,{renamed=it.take(80)},singleLine=true)},
            confirmButton={TextButton(onClick={actions.renameShelf(shelf.id,renamed);editing=null},enabled=renamed.isNotBlank()){Text("저장")}},
            dismissButton={TextButton(onClick={editing=null}){Text("취소")}})
    }
    removing?.let { shelf -> AlertDialog(onDismissRequest={removing=null},title={Text("책장을 삭제할까요?")},
        text={Text("${shelf.name}의 분류만 해제합니다. 책과 독서 기록은 그대로 유지됩니다.")},
        confirmButton={TextButton(onClick={actions.deleteShelf(shelf.id);removing=null}){Text("책장 삭제")}},
        dismissButton={TextButton(onClick={removing=null}){Text("취소")}}) }
}

@Composable
internal fun BatchImportStatus(batch: BatchImportUi, onDismiss: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("batch_import_status"),
        color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(if(batch.active) "${batch.completed+1}/${batch.total} 파일 가져오는 중"
                else if(batch.cancelled || batch.completed<batch.total) "일괄 가져오기 중단" else "일괄 가져오기 완료",
                style=MaterialTheme.typography.titleSmall)
            Text("추가 ${batch.added} · 중복 ${batch.duplicates} · 실패 ${batch.failures}",style=MaterialTheme.typography.bodySmall,
                modifier=Modifier.testTag("batch_import_counts"))
            if(!batch.active) {
                if(batch.completed<batch.total) Text("완료된 책은 서재에 남아 있습니다. 나머지 파일은 다시 선택해 주세요.",style=MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick={details=true},modifier=Modifier.testTag("batch_results")){Text("결과 보기")}
                    TextButton(onClick=onDismiss,modifier=Modifier.testTag("dismiss_batch")){Text("닫기")} }
            }
        }
    }
    if(details) AlertDialog(onDismissRequest={details=false},title={Text("파일별 가져오기 결과")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            batch.results.forEach { Text(it) }
        }
    },confirmButton={TextButton(onClick={details=false}){Text("닫기")}})
}

@Composable
internal fun LibraryStatusCard(
    message: String,
    tag: String,
    supporting: String? = null,
    isError: Boolean = false,
    @DrawableRes icon: Int? = null,
    action: @Composable () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon?.let { AppIcon(it) }
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            action()
        }
    }
}

/**
 * Locale-independent human-readable byte formatter (avoids Android Lint DefaultLocale warnings).
 */
internal fun formatReadableByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kbTenths = (bytes * 10L) / 1024L
    if (kbTenths < 10_240L) {
        return if (kbTenths < 100L) {
            "${kbTenths / 10L}.${kbTenths % 10L} KB"
        } else {
            "${(kbTenths + 5L) / 10L} KB"
        }
    }
    val mbTenths = (bytes * 10L) / (1024L * 1024L)
    return if (mbTenths < 100L) {
        "${mbTenths / 10L}.${mbTenths % 10L} MB"
    } else {
        "${(mbTenths + 5L) / 10L} MB"
    }
}
