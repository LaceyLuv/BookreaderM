package org.bookreader.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.R
import org.bookreader.mobile.library.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability

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
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(color).clearAndSetSemantics { }) {
        val rendered=bitmap
        if(rendered!=null) Image(rendered.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            Box(Modifier.fillMaxHeight().width(if(preset%2==0)8.dp else 3.dp).background(Color.White.copy(alpha=.18f)))
            Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Text(book.format.displayName,color=Color.White.copy(alpha=.75f),style=MaterialTheme.typography.labelSmall)
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(preset%3==0) HorizontalDivider(Modifier.width(24.dp),color=Color.White.copy(alpha=.65f))
                    Text(book.title,color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,
                        maxLines=5,overflow=TextOverflow.Ellipsis)
                    book.author?.let { Text(it,color=Color.White.copy(alpha=.85f),style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis) }
                }
                if(preset%3==1) HorizontalDivider(color=Color.White.copy(alpha=.35f)) else Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
internal fun LibraryBookCard(book: Book, catalog: LibraryCatalog, selected: Boolean, selecting: Boolean,
    wide: Boolean, actions: LibraryActions, onInfo: (Book)->Unit, onOpen: (Book)->Unit) {
    LaunchedEffect(book.id,book.coverKey) { if(book.availability==BookAvailability.READY) actions.ensureArtwork(book) }
    val details=catalog.details[book.id]?:BookDetails()
    val modifier=Modifier.fillMaxWidth().testTag("book_${book.id}").clip(RoundedCornerShape(16.dp))
        .combinedClickable(onClick={ if(selecting)actions.toggleSelected(book) else onOpen(book) },onLongClick={actions.toggleSelected(book)})
        .then(if(selected) Modifier.border(2.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp)) else Modifier)
    Surface(modifier,color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(16.dp)) {
        if(wide) Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            BookCover(book,details,Modifier.width(82.dp).height(123.dp))
            Column(Modifier.weight(1f)) { LibraryBookCaption(book,catalog,selected,selecting,actions,onInfo,onOpen) }
        } else Column(Modifier.padding(12.dp)) {
            BookCover(book,details,Modifier.fillMaxWidth().heightIn(max=240.dp).aspectRatio(2f/3f))
            Spacer(Modifier.height(10.dp))
            LibraryBookCaption(book,catalog,selected,selecting,actions,onInfo,onOpen)
        }
    }
}

@Composable
private fun LibraryBookCaption(book: Book,catalog: LibraryCatalog,selected: Boolean,selecting: Boolean,
    actions: LibraryActions,onInfo:(Book)->Unit,onOpen:(Book)->Unit) {
    Text(book.title,style=MaterialTheme.typography.titleSmall,maxLines=3,overflow=TextOverflow.Ellipsis)
    book.author?.let { Text(it,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis) }
    val progress=catalog.progress[book.id]
    Text(catalog.status(book).label+(progress?.let { " · ${it.toInt()}%" }?:""),style=MaterialTheme.typography.labelMedium,
        color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("reading_status_${book.id}"))
    if(progress!=null) LinearProgressIndicator(progress={ (progress/100).toFloat() },modifier=Modifier.fillMaxWidth().padding(top=6.dp))
    if(book.availability!=BookAvailability.READY) Text(book.availability.displayName,color=MaterialTheme.colorScheme.error,
        style=MaterialTheme.typography.labelMedium,modifier=Modifier.testTag("availability_${book.id}"))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        if(selecting) Checkbox(selected,{actions.toggleSelected(book)},Modifier.weight(1f).testTag("select_${book.id}"))
        else TextButton(onClick={onOpen(book)},modifier=Modifier.weight(1f).testTag("read_${book.id}")) { Text(if(book.availability==BookAvailability.READY)"읽기" else "확인") }
        IconButton(onClick={onInfo(book)},modifier=Modifier.testTag("info_${book.id}")) { AppIcon(R.drawable.ic_info,description="책 정보") }
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
        TextButton(onClick={actions.preferences { it.copy(view=if(it.view==LibraryView.GRID)LibraryView.LIST else LibraryView.GRID) }},modifier=Modifier.testTag("library_view")) {
            Text(if(preferences.view==LibraryView.GRID)"목록 보기" else "격자 보기")
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
        LaunchedEffect(state.pendingCoverKey) { state.pendingCoverKey?.let { cover=CoverEdit.Image(it) } }
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
