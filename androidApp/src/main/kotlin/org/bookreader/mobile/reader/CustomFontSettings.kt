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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bookreader.mobile.settings.ReaderFont
import org.bookreader.mobile.settings.ReaderFontWeight
import org.bookreader.mobile.settings.ReaderSettings
import kotlin.math.roundToInt

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


private data class FontWeightPreset(val weight: Int, val label: String, val tag: String)

private val FONT_WEIGHT_PRESETS = listOf(
    FontWeightPreset(300, "가늘게", "reader_weight_300"),
    FontWeightPreset(ReaderFontWeight.NORMAL.value, "보통", "reader_weight_${ReaderFontWeight.NORMAL.name}"),
    FontWeightPreset(500, "중간", "reader_weight_500"),
    FontWeightPreset(ReaderFontWeight.BOLD.value, "굵게", "reader_weight_${ReaderFontWeight.BOLD.name}"),
    FontWeightPreset(800, "아주 굵게", "reader_weight_800"),
)

private fun readerFontWeightLabel(weight: Int): String = when {
    weight < 200 -> "아주 가늘게"
    weight < 300 -> "매우 가늘게"
    weight < 400 -> "가늘게"
    weight == 400 -> "보통"
    weight < 500 -> "약간 굵게"
    weight < 600 -> "중간"
    weight < 700 -> "반굵게"
    weight < 800 -> "굵게"
    weight < 900 -> "아주 굵게"
    else -> "가장 굵게"
}

private fun stepDownFontWeight(weight: Int): Int {
    val step = ReaderSettings.FONT_WEIGHT_STEP
    val target = if (weight % step == 0) weight - step else (weight / step) * step
    return target.coerceIn(ReaderSettings.MIN_FONT_WEIGHT, ReaderSettings.MAX_FONT_WEIGHT)
}

private fun stepUpFontWeight(weight: Int): Int {
    val step = ReaderSettings.FONT_WEIGHT_STEP
    val target = if (weight % step == 0) weight + step else ((weight / step) + 1) * step
    return target.coerceIn(ReaderSettings.MIN_FONT_WEIGHT, ReaderSettings.MAX_FONT_WEIGHT)
}

@Composable
fun ReaderFontWeightSettings(settings: ReaderSettings, epubMode: Boolean, onChange: (ReaderSettings) -> Unit) {
    val weight = settings.effectiveFontWeight
    val context = LocalContext.current
    val customVariableRange = remember(settings.customFontId, context) {
        UserFontStore(context).find(settings.customFontId)?.let { FontFilePolicy.variableWeightRange(it.file) }
    }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    fun applyWeight(target: Int) {
        val updated = settings.withFontWeight(target)
        onChange(
            if (epubMode) updated.copy(epubPublisherStyles = false) else updated,
        )
    }
    val supportNote = when {
        settings.customFontId != null && customVariableRange != null ->
            "가변 글꼴 적용 중 (${customVariableRange.first}~${customVariableRange.last} 범위 세밀 조절 지원)"
        settings.customFontId != null ->
            "단일 굵기 사용자 글꼴입니다. 보통·굵게 단계 중심으로 표시됩니다."
        settings.font == ReaderFont.SANS || settings.font == ReaderFont.SERIF ->
            "기본 고딕·기본 명조는 50 단위로 세밀하게 조절됩니다."
        else ->
            "나눔고딕·나눔명조는 단일 굵기 글꼴이라 보통·굵게 중심으로 표시됩니다. 세밀한 조절은 기본 글꼴이나 가변 글꼴을 사용하세요."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "글자 굵기",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "$weight · ${readerFontWeightLabel(weight)}",
                        modifier = Modifier.testTag("font_weight_value"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { applyWeight(stepDownFontWeight(weight)) },
                            enabled = weight > ReaderSettings.MIN_FONT_WEIGHT,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                                .testTag("font_weight_decrease")
                                .semantics { contentDescription = "글자 굵기 줄이기" },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("−", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "$weight",
                            modifier = Modifier
                                .widthIn(min = 44.dp)
                                .padding(horizontal = 4.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight(weight),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        FilledTonalButton(
                            onClick = { applyWeight(stepUpFontWeight(weight)) },
                            enabled = weight < ReaderSettings.MAX_FONT_WEIGHT,
                            shape = RoundedCornerShape(9.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                                .testTag("font_weight_increase")
                                .semantics { contentDescription = "글자 굵기 늘리기" },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = weight.toFloat(),
                    onValueChange = { raw ->
                        val step = ReaderSettings.FONT_WEIGHT_STEP
                        val snapped = ((raw / step).roundToInt() * step)
                            .coerceIn(ReaderSettings.MIN_FONT_WEIGHT, ReaderSettings.MAX_FONT_WEIGHT)
                        if (snapped != weight) applyWeight(snapped)
                    },
                    valueRange = ReaderSettings.MIN_FONT_WEIGHT.toFloat()..ReaderSettings.MAX_FONT_WEIGHT.toFloat(),
                    steps = (ReaderSettings.MAX_FONT_WEIGHT - ReaderSettings.MIN_FONT_WEIGHT) / ReaderSettings.FONT_WEIGHT_STEP - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_weight_slider")
                        .semantics { contentDescription = "글자 굵기 조절" },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "100 가늘게",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "400 보통",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "900 가장 굵게",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FONT_WEIGHT_PRESETS.forEach { preset ->
                    val selected = weight == preset.weight
                    FilterChip(
                        selected = selected,
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier.testTag(preset.tag),
                        onClick = { applyWeight(preset.weight) },
                        label = {
                            Text(
                                text = preset.label,
                                fontWeight = FontWeight(preset.weight),
                            )
                        },
                    )
                }
            }

            Text(
                text = supportNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
