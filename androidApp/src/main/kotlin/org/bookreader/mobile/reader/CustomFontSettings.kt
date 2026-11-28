package org.bookreader.mobile.reader

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bookreader.mobile.settings.ReaderFontWeight
import org.bookreader.mobile.settings.ReaderSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomFontSettings(
    settings: ReaderSettings,
    appliedFontId: String?,
    epubMode: Boolean,
    onBusy: (Boolean) -> Unit,
    onChange: (ReaderSettings) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { UserFontStore(context) }
    val scope = rememberCoroutineScope()
    var fonts by remember { mutableStateOf<List<UserFont>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<UserFont?>(null) }
    val current by rememberUpdatedState(settings)
    LaunchedEffect(store) {
        withContext(Dispatchers.Main.immediate) {
            fonts = withContext(Dispatchers.IO) { store.list() }
        }
    }
    fun select(id: String?) {
        onChange(
            current.copy(
                customFontId = id,
                epubPublisherStyles = if (epubMode) false else current.epubPublisherStyles,
            ),
        )
    }
    val documentContract = remember {
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent =
                super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
        }
    }
    val picker = rememberLauncherForActivityResult(documentContract) { uri ->
        if (uri != null && !busy) {
            scope.launch(Dispatchers.Main.immediate) {
                busy = true
                onBusy(true)
                message = null
                try {
                    val imported = store.import(uri)
                    fonts = withContext(Dispatchers.IO) { store.list() }
                    select(imported.id)
                    message = "${imported.label} 등록 완료. 적용을 누르면 본문에 반영됩니다."
                } catch (_: TimeoutCancellationException) {
                    message = "파일을 읽는 시간이 초과됐습니다. 기기에 저장된 글꼴을 선택하세요."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    message = if (failure is IllegalArgumentException || failure is IllegalStateException) {
                        failure.message
                    } else {
                        "글꼴을 가져오지 못했습니다. 파일과 저장 공간을 확인하세요."
                    }
                } finally {
                    busy = false
                    onBusy(false)
                }
            }
        }
    }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "내 글꼴",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "TTF·OTF 파일 · 파일당 20MB 이하",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.testTag("reader_font_import"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    onClick = {
                        try {
                            picker.launch(arrayOf("*/*"))
                        } catch (_: Exception) {
                            message = "파일 선택기를 열 수 없습니다."
                        }
                    },
                ) {
                    Text(
                        text = if (busy) "글꼴 가져오는 중…" else "글꼴 파일 추가",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (fonts.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    fonts.forEach { font ->
                        val isSelected = settings.customFontId == font.id
                        val isApplied = font.id == appliedFontId
                        val canRemove = !busy && !isApplied
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = isSelected,
                                enabled = !busy,
                                onClick = { select(font.id) },
                                colors = chipColors,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = !busy,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                ),
                                label = {
                                    Text(
                                        text = font.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingIcon = if (isApplied) {
                                    {
                                        Text(
                                            text = "적용 중",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reader_custom_font_${font.id}"),
                            )
                            TextButton(
                                enabled = canRemove,
                                onClick = { remove = font },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reader_font_remove_${font.id}"),
                            ) {
                                Text(
                                    text = "삭제",
                                    color = if (canRemove) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (appliedFontId != null) {
                Text(
                    text = "사용 중인 글꼴은 다른 글꼴을 적용한 뒤 삭제할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.customFontId != null && fonts.none { it.id == settings.customFontId }) {
                Text(
                    text = "등록된 글꼴을 찾을 수 없어 기본 글꼴로 표시합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message?.let { notice ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = notice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("reader_font_message"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

        }
    }

    remove?.let { font ->
        AlertDialog(
            onDismissRequest = { remove = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("글꼴 삭제", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${font.label}을 앱에서 삭제합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Text(
                            text = "원본 파일은 유지됩니다.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("reader_font_confirm_remove"),
                    onClick = {
                        remove = null
                        scope.launch(Dispatchers.Main.immediate) {
                            busy = true
                            onBusy(true)
                            try {
                                store.remove(font.id)
                                if (current.customFontId == font.id) select(null)
                                fonts = withContext(Dispatchers.IO) { store.list() }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                message = "글꼴을 삭제하지 못했습니다."
                            } finally {
                                busy = false
                                onBusy(false)
                            }
                        }
                    },
                ) {
                    Text(
                        text = "삭제",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { remove = null }) {
                    Text("취소")
                }
            },
        )
    }
}


@Composable
fun ReaderFontWeightSettings(settings: ReaderSettings, epubMode: Boolean, onChange: (ReaderSettings) -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "글자 굵기",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderFontWeight.entries.forEach { weight ->
                val selected = settings.fontWeight == weight
                FilterChip(
                    selected = selected,
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.testTag("reader_weight_${weight.name}"),
                    onClick = {
                        onChange(
                            settings.copy(
                                fontWeight = weight,
                                epubPublisherStyles = if (epubMode) false else settings.epubPublisherStyles,
                            ),
                        )
                    },
                    label = {
                        Text(
                            text = if (weight == ReaderFontWeight.NORMAL) "보통" else "굵게",
                            fontWeight = FontWeight(weight.value),
                        )
                    },
                )
            }
        }
    }
}
