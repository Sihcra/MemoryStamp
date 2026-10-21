package com.memorystamp.app

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** A letter to future me. It stays sealed until [openAt] (8:00 on the chosen day). */
data class Letter(val id: Long, val title: String, val body: String, val stampId: String?, val openAt: Long, val opened: Boolean) {
    val ready get() = System.currentTimeMillis() >= openAt
    val openDay: LocalDate get() = Instant.ofEpochMilli(openAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val writtenDay: LocalDate get() = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault()).toLocalDate()
}

private val DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

object Letters {
    val all = mutableStateListOf<Letter>()

    fun file(context: Context) = File(context.filesDir, "letters.json")

    fun load(context: Context) {
        all.clear()
        val f = file(context)
        if (!f.exists()) return
        runCatching {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                all += Letter(o.getLong("id"), o.getString("title"), o.getString("body"), o.optString("stampId").ifEmpty { null }, o.getLong("openAt"), o.getBoolean("opened"))
            }
        }
    }

    fun put(context: Context, letter: Letter) {
        val i = all.indexOfFirst { it.id == letter.id }
        if (i >= 0) all[i] = letter else all.add(0, letter)
        val arr = JSONArray()
        all.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("stampId", it.stampId ?: "").put("openAt", it.openAt).put("opened", it.opened)) }
        val tmp = File(file(context).path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file(context))
    }

    /** One inexact alarm per sealed letter; [LetterAlarm] posts the notification. */
    fun schedule(context: Context, letter: Letter) {
        if (letter.ready) return
        val pi = PendingIntent.getBroadcast(
            context, letter.id.toInt(),
            Intent(context, LetterAlarm::class.java).putExtra("title", letter.title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC_WAKEUP, letter.openAt, pi)
    }
}

class LetterAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Alarms are wiped on reboot; put them back.
            Letters.load(context)
            Letters.all.forEach { Letters.schedule(context, it) }
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("letters", "Thư gửi tương lai", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).putExtra("tab", 3), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, "letters")
            .setSmallIcon(R.drawable.ic_mail)
            .setContentTitle("Có một lá thư từ quá khứ 💌")
            .setContentText(intent.getStringExtra("title")?.ifBlank { null } ?: "Bạn của ngày xưa vừa gửi thư tới")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), n) }
        }
    }
}

@Composable
fun LettersScreen() {
    val context = LocalContext.current
    var writing by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf<Letter?>(null) }
    reading?.let { ReadLetter(it) { reading = null }; return }
    if (writing) { WriteLetter { writing = false }; return }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Thư gửi tương lai", style = MaterialTheme.typography.titleLarge)
                    Text("viết hôm nay, mở vào một ngày khác", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                }
                Bubble(R.drawable.ic_plus, "Viết thư mới", color = Palette.Pink) { writing = true }
            }
        }
        if (Letters.all.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Envelope(Palette.PinkSoft, Modifier.size(140.dp, 96.dp))
                Text("Chưa có lá thư nào.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text("Viết cho chính mình của 1 năm sau nhé!", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
        }
        items(Letters.all, key = { it.id }) { l ->
            val tint = when {
                l.opened -> Palette.Paper
                l.ready -> Palette.Pink
                else -> listOf(Palette.PinkSoft, Palette.Mint, Palette.Butter, Palette.Sky, Palette.Lilac)[(l.id % 5).toInt()]
            }
            Row(
                Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Palette.Paper)
                    .clickable {
                        if (l.ready) {
                            if (!l.opened) Letters.put(context, l.copy(opened = true))
                            reading = l
                        }
                    }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Envelope(tint, Modifier.size(64.dp, 44.dp).rotate(tiltOf(l.id)))
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    when {
                        l.opened -> {
                            Text(l.title.ifBlank { "Thư ngày ${l.writtenDay.format(DMY)}" }, style = MaterialTheme.typography.titleMedium)
                            Text("đã mở · viết ngày ${l.writtenDay.format(DMY)}", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                        }
                        l.ready -> {
                            Text("Đã đến ngày rồi!", style = MaterialTheme.typography.titleMedium, color = Palette.Rose)
                            Text("chạm để mở thư", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                        }
                        else -> {
                            Text("Mở vào ${l.openDay.format(DMY)}", style = MaterialTheme.typography.titleMedium)
                            Text("còn ${ChronoUnit.DAYS.between(LocalDate.now(), l.openDay)} ngày · đang niêm phong", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun Envelope(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.12f)
        drawRoundRect(color, cornerRadius = r)
        drawRoundRect(Palette.Ink.copy(alpha = 0.15f), cornerRadius = r, style = Stroke(2f))
        drawPath(Path().apply {
            moveTo(size.width * 0.06f, size.height * 0.1f)
            lineTo(size.width / 2, size.height * 0.58f)
            lineTo(size.width * 0.94f, size.height * 0.1f)
        }, Palette.Ink.copy(alpha = 0.25f), style = Stroke(3f))
        drawCircle(Palette.Rose, size.minDimension * 0.13f, Offset(size.width / 2, size.height * 0.58f))
    }
}

@Composable
private fun WriteLetter(onDone: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var stampId by remember { mutableStateOf<String?>(null) }
    var openDay by remember { mutableStateOf(LocalDate.now().plusYears(1)) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    BackHandler(onBack = onDone)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Bubble(R.drawable.ic_back, "Quay lại", onClick = onDone)
            Text("Thư cho tương lai", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        // The letter paper: soft lines, no borders.
        Column(
            Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)).background(Palette.Paper, RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Gửi tôi của ngày ${openDay.format(DMY)},", style = MaterialTheme.typography.labelLarge, color = Palette.Rose)
            Field(title, { title = it }, "Tiêu đề (không bắt buộc)", MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().size(1.dp).background(Palette.PinkSoft))
            Field(body, { body = it }, "Hôm nay mình muốn kể...", MaterialTheme.typography.bodyLarge, minHeight = 160)
        }

        if (Stamps.all.isNotEmpty()) {
            Text("Kẹp một con tem", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Stamps.all.take(30), key = { it.id }) { s ->
                    val on = stampId == s.id.toString()
                    Thumb(
                        s.image, 200,
                        Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                            .then(if (on) Modifier.border(2.dp, Palette.Rose, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { stampId = if (on) null else s.id.toString() },
                    )
                }
            }
        }

        Text("Mở vào ngày", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val now = LocalDate.now()
            listOf("1 tuần" to now.plusWeeks(1), "1 tháng" to now.plusMonths(1), "6 tháng" to now.plusMonths(6), "1 năm" to now.plusYears(1)).forEach { (label, d) ->
                Pill(label, openDay == d) { openDay = d }
            }
        }
        Pill("Chọn ngày khác: ${openDay.format(DMY)}", false) {
            DatePickerDialog(context, { _, y, m, d -> openDay = LocalDate.of(y, m + 1, d) }, openDay.year, openDay.monthValue - 1, openDay.dayOfMonth).apply {
                datePicker.minDate = System.currentTimeMillis() + 86_400_000L
            }.show()
        }

        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(CircleShape)
                .background(if (body.isBlank()) Palette.PinkSoft else Palette.Rose)
                .clickable(enabled = body.isNotBlank()) {
                    val openAt = openDay.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val letter = Letter(System.currentTimeMillis(), title.trim(), body.trim(), stampId, openAt, false)
                    Letters.put(context, letter)
                    Letters.schedule(context, letter)
                    if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onDone()
                }
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Niêm phong và gửi 💌", style = MaterialTheme.typography.labelLarge, color = if (body.isBlank()) Palette.InkSoft else Color.White) }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, style: androidx.compose.ui.text.TextStyle, minHeight: Int = 0) {
    BasicTextField(
        value, onChange,
        textStyle = style,
        cursorBrush = SolidColor(Palette.Rose),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).heightIn(min = minHeight.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = style, color = Palette.InkSoft); inner() } },
    )
}

@Composable
private fun ReadLetter(letter: Letter, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Bubble(R.drawable.ic_back, "Quay lại", onClick = onClose)
        }
        Column(
            Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)).background(Palette.Paper, RoundedCornerShape(24.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Viết ngày ${letter.writtenDay.format(DMY)}", style = MaterialTheme.typography.labelMedium, color = Palette.Rose)
            if (letter.title.isNotBlank()) Text(letter.title, style = MaterialTheme.typography.titleLarge)
            Text(letter.body, style = MaterialTheme.typography.bodyLarge)
            Stamps.all.firstOrNull { it.id.toString() == letter.stampId }?.let {
                Thumb(it.image, 600, Modifier.align(Alignment.End).size(120.dp).rotate(tiltOf(it.id)))
            }
        }
    }
}
