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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun JournalScreen(onSettings: () -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var open by remember { mutableStateOf<Stamp?>(null) }

    open?.let { s ->
        StampDetail(s, onClose = { open = null })
        return
    }

    val byDay = Stamps.all.groupBy { it.day }
    val blanks = List(month.atDay(1).dayOfWeek.value - 1) { null }
    val days = blanks + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val dayStamps = byDay[selectedDay].orEmpty()
    val today = LocalDate.now()

    LazyVerticalGrid(
        GridCells.Fixed(7),
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(7) }) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nhật ký tem", style = MaterialTheme.typography.titleLarge)
                    Text("${Stamps.all.size} tem đã sưu tầm", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                }
                Bubble(R.drawable.ic_gear, "Cài đặt", onClick = onSettings)
            }
        }
        item(span = { GridItemSpan(7) }) {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Bubble(R.drawable.ic_left, "Tháng trước", size = 36.dp) { month = month.minusMonths(1) }
                Text("tháng ${month.monthValue} · ${month.year}", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Bubble(R.drawable.ic_right, "Tháng sau", size = 36.dp) { month = month.plusMonths(1) }
            }
        }
        items(listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")) {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft, textAlign = TextAlign.Center)
        }
        items(days) { day ->
            if (day == null) {
                Spacer(Modifier.aspectRatio(1f))
            } else {
                val cover = byDay[day]?.firstOrNull()
                val selected = day == selectedDay
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (day == today) Palette.PinkSoft else Palette.Paper)
                        .then(if (selected) Modifier.border(2.dp, Palette.Rose, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { selectedDay = day },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        Thumb(cover.image, 120, Modifier.fillMaxSize().padding(3.dp).rotate(tiltOf(cover.id)))
                    } else {
                        Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
                    }
                }
            }
        }
        item(span = { GridItemSpan(7) }) {
            Text(
                if (dayStamps.isEmpty()) "${selectedDay.dayOfMonth}/${selectedDay.monthValue} · chưa có tem nào ✿" else "${selectedDay.dayOfMonth}/${selectedDay.monthValue} · ${dayStamps.size} tem",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
        }
        items(dayStamps, key = { it.id }, span = { GridItemSpan(7 / 2) }) { s ->
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { open = s }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(s.image, 400, Modifier.fillMaxWidth().aspectRatio(1f).rotate(tiltOf(s.id)))
                Text(s.title.ifBlank { s.film }, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item(span = { GridItemSpan(7) }) { Spacer(Modifier.size(24.dp)) }
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
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Palette.Rose, unfocusedBorderColor = Palette.PinkSoft,
        focusedContainerColor = Palette.Paper, unfocusedContainerColor = Palette.Paper,
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Bubble(R.drawable.ic_back, "Quay lại", onClick = ::saveAndClose)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("${stamp.day.dayOfMonth}/${stamp.day.monthValue}/${stamp.day.year}", style = MaterialTheme.typography.titleMedium)
                Text("filter ${stamp.film}", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
            Bubble(R.drawable.ic_share, "Chia sẻ") {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", stamp.image)
                val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Chia sẻ tem"))
            }
            Spacer(Modifier.size(8.dp))
            Bubble(R.drawable.ic_trash, "Xóa tem") { confirmDelete = true }
        }
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Thumb(stamp.image, 1200, Modifier.fillMaxWidth().aspectRatio(1f).rotate(tiltOf(stamp.id)).shadow(6.dp))
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), placeholder = { Text("Đặt tên cho tem") }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = fieldColors)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = { Text("Hôm nay có gì vui?") }, minLines = 3, shape = RoundedCornerShape(18.dp), colors = fieldColors)
        Box(
            Modifier.fillMaxWidth().clip(CircleShape).background(Palette.Rose).clickable(onClick = ::saveAndClose).padding(14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Lưu", style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White) }
        Spacer(Modifier.size(12.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Palette.Paper,
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
