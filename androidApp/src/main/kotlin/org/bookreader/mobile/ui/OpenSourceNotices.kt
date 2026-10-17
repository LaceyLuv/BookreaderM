package org.bookreader.mobile.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OpenSourceNotices() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("오픈소스 라이선스") }
    if (open) {
        val context = LocalContext.current.applicationContext
        val text by produceState<String?>(null, context) {
            value = try { withContext(Dispatchers.IO) {
                context.assets.open("third-party-notices.txt").bufferedReader().use { reader ->
                    val bounded = CharArray(128 * 1024)
                    var count = 0
                    while (count < bounded.size) {
                        val read = reader.read(bounded, count, bounded.size - count)
                        if (read == -1) break
                        count += read
                    }
                    check(reader.read() == -1)
                    String(bounded, 0, count)
                }
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { "라이선스 안내를 읽지 못했습니다. 다시 열어 주세요." }
        }
        AlertDialog(onDismissRequest = { open = false }, title = { Text("오픈소스 라이선스") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(text?.split("\n\n") ?: listOf("불러오는 중…")) { Text(it) }
            } }, confirmButton = { TextButton(onClick = { open = false }) { Text("닫기") } })
    }
}
