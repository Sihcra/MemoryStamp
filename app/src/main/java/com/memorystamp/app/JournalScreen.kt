package com.memorystamp.app

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun JournalScreen() {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var open by remember { mutableStateOf<Stamp?>(null) }

    open?.let { s ->
        StampDetail(s, onClose = { open = null })
        return
    }

    val byDay = Stamps.all.groupBy { it.day }
    val firstDow = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val blanks = List(firstDow) { null }
    val days = blanks + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val dayStamps = byDay[selectedDay].orEmpty()

    LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item(span = { GridItemSpan(7) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Tháng trước") }
                Text("Tháng ${month.monthValue}, ${month.year}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton({ month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Tháng sau") }
            }
        }
        items(listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")) {
            Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
        }
        items(days) { day ->
            if (day == null) {
                Spacer(Modifier.aspectRatio(1f))
            } else {
                val cover = byDay[day]?.firstOrNull()
                val selected = day == selectedDay
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable { selectedDay = day },
                ) {
                    cover?.let { Thumb(it.image, 120, Modifier.fillMaxSize().padding(2.dp)) }
                    Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(3.dp))
                }
            }
        }
        item(span = { GridItemSpan(7) }) {
            Text(
                if (dayStamps.isEmpty()) "Ngày ${selectedDay.dayOfMonth}/${selectedDay.monthValue}: chưa có tem nào" else "Ngày ${selectedDay.dayOfMonth}/${selectedDay.monthValue}: ${dayStamps.size} tem",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        items(dayStamps, key = { it.id }, span = { GridItemSpan(7 / 2) }) { s ->
            Column(Modifier.clickable { open = s }) {
                Thumb(s.image, 400, Modifier.fillMaxWidth().aspectRatio(1f))
                Text(s.title.ifBlank { s.film }, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        item(span = { GridItemSpan(7) }) {
            Text("Tất cả: ${Stamps.all.size} tem", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun StampDetail(stamp: Stamp, onClose: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(stamp.title) }
    var note by remember { mutableStateOf(stamp.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun saveAndClose() {
        if (title != stamp.title || note != stamp.note) Stamps.update(stamp.copy(title = title, note = note))
        onClose()
    }
    BackHandler { saveAndClose() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(::saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") }
            Text("${stamp.day.dayOfMonth}/${stamp.day.monthValue}/${stamp.day.year} · ${stamp.film}", style = MaterialTheme.typography.titleMedium)
        }
        Thumb(stamp.image, 1200, Modifier.fillMaxWidth().aspectRatio(1f))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Tiêu đề") }, singleLine = true)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Ghi chú") }, minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(::saveAndClose) { Text("Lưu") }
            OutlinedButton({
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", stamp.image)
                val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Chia sẻ tem"))
            }) { Text("Chia sẻ") }
            OutlinedButton({ confirmDelete = true }) { Text("Xóa") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa tem này?") },
            text = { Text("Tem sẽ bị xóa khỏi máy và không khôi phục được (trừ khi bạn đã xuất sao lưu).") },
            confirmButton = { TextButton({ Stamps.delete(stamp); onClose() }) { Text("Xóa") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Hủy") } },
        )
    }
}

@Composable
fun Thumb(file: File, px: Int, modifier: Modifier) {
    val bmp by produceState<Bitmap?>(null, file, px) { value = withContext(Dispatchers.IO) { loadThumb(file, px) } }
    Box(modifier) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) } }
}
