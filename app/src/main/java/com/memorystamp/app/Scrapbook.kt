package com.memorystamp.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/** Something placed on a page. x/y are the centre as a fraction of page width/height. */
data class Item(
    val id: Long,
    val kind: String, // "stamp" (value = stamp id), "text", "sticker" (value = emoji)
    val value: String,
    val x: Float = 0.5f,
    val y: Float = 0.45f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val color: Int = 0xFF262420.toInt(),
)

data class Page(val id: Long, val bg: Int, val items: List<Item>)

val PAGE_COLORS = listOf(Palette.Paper, Palette.Blush, Palette.Mint, Palette.Butter, Palette.Sky, Palette.Lilac)
val TEXT_COLORS = listOf(Color(0xFF262420), Color(0xFFC0575B), Color(0xFF5E9C7E), Color(0xFF5B7FB8), Color(0xFFC99A3A))
val STICKERS = listOf("♡", "✿", "☆", "☁", "♪", "✧", "🎀", "🌷", "🍓", "🌙", "☀️", "🐻", "🐰", "🍰", "💌", "🌈")

// Base sizes as a fraction of page width; the same numbers drive the screen and the exported PNG.
private const val STAMP_W = 0.34f
private const val STICKER_SIZE = 0.11f
private const val TEXT_SIZE = 0.06f
private const val TEXT_BOX_W = 0.6f

object Pages {
    val all = mutableStateListOf<Page>()
    private lateinit var file: File

    fun load(context: Context) {
        file = File(context.filesDir, "pages.json")
        all.clear()
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val items = p.getJSONArray("items")
                all += Page(p.getLong("id"), p.getInt("bg"), List(items.length()) { j ->
                    val o = items.getJSONObject(j)
                    Item(o.getLong("id"), o.getString("kind"), o.getString("value"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("scale").toFloat(), o.getDouble("rotation").toFloat(), o.getInt("color"))
                })
            }
        }
    }

    fun put(page: Page) {
        val i = all.indexOfFirst { it.id == page.id }
        if (i >= 0) all[i] = page else all.add(0, page)
        save()
    }

    fun delete(page: Page) {
        all.removeAll { it.id == page.id }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        all.forEach { p ->
            val items = JSONArray()
            p.items.forEach { items.put(JSONObject().put("id", it.id).put("kind", it.kind).put("value", it.value).put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("scale", it.scale.toDouble()).put("rotation", it.rotation.toDouble()).put("color", it.color)) }
            arr.put(JSONObject().put("id", p.id).put("bg", p.bg).put("items", items))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}

/** Draws a page to a 3:4 bitmap, matching what the editor shows. */
fun renderPage(context: Context, page: Page, w: Int = 1080): Bitmap {
    val h = w * 4 / 3
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(out)
    c.drawColor(page.bg)
    val font = ResourcesCompat.getFont(context, R.font.nunito)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    page.items.forEach { item ->
        c.save()
        c.translate(item.x * w, item.y * h)
        c.rotate(item.rotation)
        c.scale(item.scale, item.scale)
        when (item.kind) {
            "stamp" -> Stamps.all.firstOrNull { it.id.toString() == item.value }?.let { s ->
                loadThumb(s.image, (w * STAMP_W * 2).toInt())?.let { bmp ->
                    val hw = w * STAMP_W / 2
                    val hh = hw * bmp.height / bmp.width
                    c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), RectF(-hw, -hh, hw, hh), paint)
                }
            }
            "sticker" -> {
                paint.typeface = Typeface.DEFAULT
                paint.textSize = w * STICKER_SIZE
                paint.color = item.color
                paint.textAlign = Paint.Align.CENTER
                c.drawText(item.value, 0f, paint.textSize * 0.35f, paint)
            }
            else -> {
                paint.typeface = Typeface.create(font ?: Typeface.DEFAULT, 700, false)
                paint.textSize = w * TEXT_SIZE
                paint.color = item.color
                paint.textAlign = Paint.Align.CENTER
                val lines = item.value.lines()
                val lh = paint.textSize * 1.25f
                lines.forEachIndexed { i, line -> c.drawText(line, 0f, (i - (lines.size - 1) / 2f) * lh + paint.textSize * 0.35f, paint) }
            }
        }
        c.restore()
    }
    return out
}

@Composable
fun ScrapbookScreen() {
    var open by remember { mutableStateOf<Page?>(null) }
    open?.let { PageEditor(it) { open = null }; return }

    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.padding(top = 12.dp)) {
                Text("Sổ tay", style = MaterialTheme.typography.titleLarge)
                Text("dán tem, viết chữ, thêm sticker", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
        }
        item {
            Box(
                Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(20.dp)).border(2.dp, Palette.Line, RoundedCornerShape(20.dp))
                    .clickable {
                        val page = Page(System.currentTimeMillis(), PAGE_COLORS[Pages.all.size % PAGE_COLORS.size].toArgb(), emptyList())
                        Pages.put(page)
                        open = page
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Bubble(R.drawable.ic_plus, "Trang mới") {}
                    Text("Trang mới", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        items(Pages.all, key = { it.id }) { page ->
            Box(Modifier.shadow(4.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).clickable { open = page }) {
                PageCanvas(page, selected = null, onSelect = {}, onChange = {})
            }
        }
    }
}

/** The page itself. When [onChange] gets edits, items can be dragged, pinched and twisted. */
@Composable
private fun PageCanvas(page: Page, selected: Long?, onSelect: (Long?) -> Unit, onChange: ((Item) -> Unit)?, editable: Boolean = false) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color(page.bg))
            .then(if (editable) Modifier.pointerInput(Unit) { detectTapGestures { onSelect(null) } } else Modifier),
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        page.items.forEach { item -> key(item.id) {
            var size by remember(item.id) { mutableStateOf(IntSize.Zero) }
            var live by remember(item.id) { mutableStateOf(item) }
            if (live.value != item.value || live.color != item.color) live = item
            // Save once the finger rests instead of on every frame.
            LaunchedEffect(live) { if (live != item) { delay(250); onChange?.invoke(live) } }
            val sel = editable && selected == item.id
            Box(
                Modifier
                    .onSizeChanged { size = it }
                    .graphicsLayer {
                        translationX = live.x * w - size.width / 2f
                        translationY = live.y * h - size.height / 2f
                        scaleX = live.scale
                        scaleY = live.scale
                        rotationZ = live.rotation
                    }
                    .then(if (sel) Modifier.border(1.5.dp, Palette.Accent, RoundedCornerShape(8.dp)) else Modifier)
                    .then(
                        if (editable) Modifier.pointerInput(item.id) {
                            detectTransformGestures(onGesture = { _, pan, zoom, rot ->
                                onSelect(item.id)
                                // pan arrives in the item's own rotated/scaled space; turn it back into page space.
                                val r = Math.toRadians(live.rotation.toDouble())
                                val px = (pan.x * cos(r) - pan.y * sin(r)).toFloat() * live.scale
                                val py = (pan.x * sin(r) + pan.y * cos(r)).toFloat() * live.scale
                                live = live.copy(
                                    x = (live.x + px / w).coerceIn(0f, 1f),
                                    y = (live.y + py / h).coerceIn(0f, 1f),
                                    scale = (live.scale * zoom).coerceIn(0.3f, 4f),
                                    rotation = live.rotation + rot,
                                )
                            })
                        }.pointerInput(item.id) { detectTapGestures { onSelect(item.id) } } else Modifier,
                    )
                    .padding(4.dp),
            ) {
                with(density) {
                    when (item.kind) {
                        "stamp" -> Stamps.all.firstOrNull { it.id.toString() == item.value }?.let {
                            Thumb(it.image, 500, Modifier.size((w * STAMP_W).toDp(), (w * STAMP_W * 1.3f).toDp()))
                        }
                        "sticker" -> Text(item.value, fontSize = (w * STICKER_SIZE).toSp(), color = Color(item.color))
                        else -> Text(
                            item.value,
                            fontSize = (w * TEXT_SIZE).toSp(),
                            lineHeight = (w * TEXT_SIZE * 1.25f).toSp(),
                            color = Color(item.color),
                            fontFamily = Nunito,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width((w * TEXT_BOX_W).toDp()),
                        )
                    }
                }
            }
        } }
    }
}

@Composable
private fun PageEditor(start: Page, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(start) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var panel by remember { mutableStateOf("") }
    var editingText by remember { mutableStateOf<Item?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun commit(p: Page) { page = p; Pages.put(p) }
    fun add(item: Item) { commit(page.copy(items = page.items + item)); selected = item.id; panel = "" }
    BackHandler { onClose() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Bubble(R.drawable.ic_back, "Quay lại", onClick = onClose)
            Spacer(Modifier.weight(1f))
            Bubble(R.drawable.ic_save, "Lưu vào thư viện ảnh") {
                scope.launch {
                    withContext(Dispatchers.IO) { savePageToGallery(context, page) }
                    Toast.makeText(context, "Đã lưu vào thư viện ảnh", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.size(8.dp))
            Bubble(R.drawable.ic_share, "Chia sẻ trang") { scope.launch { sharePage(context, page) } }
            Spacer(Modifier.size(8.dp))
            Bubble(R.drawable.ic_trash, "Xóa trang") { confirmDelete = true }
        }
        Box(Modifier.shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))) {
            PageCanvas(
                page,
                selected,
                onSelect = { selected = it },
                onChange = { changed -> commit(page.copy(items = page.items.map { if (it.id == changed.id) changed else it })) },
                editable = true,
            )
        }
        Spacer(Modifier.size(12.dp))

        val sel = page.items.firstOrNull { it.id == selected }
        if (sel != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sel.kind != "stamp") TEXT_COLORS.forEach { c ->
                    Box(Modifier.size(28.dp).clip(CircleShape).background(c).clickable {
                        commit(page.copy(items = page.items.map { if (it.id == sel.id) it.copy(color = c.toArgb()) else it }))
                    })
                }
                if (sel.kind == "text") Pill("Sửa chữ", false) { editingText = sel }
                Pill("Lên trên", false) { commit(page.copy(items = page.items.filter { it.id != sel.id } + sel)) }
                Spacer(Modifier.weight(1f))
                Bubble(R.drawable.ic_trash, "Xóa mục này", size = 36.dp) {
                    commit(page.copy(items = page.items.filter { it.id != sel.id })); selected = null
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Bubble(R.drawable.ic_camera, "Thêm tem", color = if (panel == "stamp") Palette.Line else Palette.Soft) { panel = if (panel == "stamp") "" else "stamp" }
                Bubble(R.drawable.ic_text, "Thêm chữ") { editingText = Item(System.currentTimeMillis(), "text", "") }
                Bubble(R.drawable.ic_heart, "Thêm sticker", color = if (panel == "sticker") Palette.Line else Palette.Soft) { panel = if (panel == "sticker") "" else "sticker" }
                Bubble(R.drawable.ic_palette, "Màu nền", color = if (panel == "bg") Palette.Line else Palette.Soft) { panel = if (panel == "bg") "" else "bg" }
            }
        }
        Spacer(Modifier.size(10.dp))
        when (panel) {
            "stamp" -> if (Stamps.all.isEmpty()) Text("Chưa có tem nào, qua tab Chụp để chụp nhé.", style = MaterialTheme.typography.bodySmall)
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Stamps.all, key = { it.id }) { s ->
                    Thumb(s.image, 200, Modifier.size(72.dp).clickable { add(Item(System.currentTimeMillis(), "stamp", s.id.toString(), rotation = tiltOf(s.id))) })
                }
            }
            "sticker" -> LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(STICKERS) { e ->
                    Text(e, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clip(CircleShape).clickable {
                        add(Item(System.currentTimeMillis(), "sticker", e, color = TEXT_COLORS[1].toArgb()))
                    }.padding(8.dp))
                }
            }
            "bg" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PAGE_COLORS.forEach { c ->
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c).border(1.dp, Palette.Line, CircleShape).clickable { commit(page.copy(bg = c.toArgb())) })
                }
            }
        }
    }

    editingText?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.value) }
        AlertDialog(
            onDismissRequest = { editingText = null },
            title = { Text("Viết gì đó") },
            text = { OutlinedTextField(text, { text = it }, minLines = 2) },
            confirmButton = {
                TextButton({
                    if (text.isNotBlank()) {
                        if (page.items.any { it.id == item.id }) commit(page.copy(items = page.items.map { if (it.id == item.id) it.copy(value = text) else it }))
                        else add(item.copy(value = text))
                    }
                    editingText = null
                }) { Text("Xong") }
            },
            dismissButton = { TextButton({ editingText = null }) { Text("Hủy") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa trang này?") },
            text = { Text("Tem gốc vẫn còn trong Nhật ký, chỉ trang sổ tay bị xóa.") },
            confirmButton = { TextButton({ Pages.delete(page); onClose() }) { Text("Xóa") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Hủy") } },
        )
    }
}

private fun savePageToGallery(context: Context, page: Page) {
    val bmp = renderPage(context, page)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "MemoryStamp_page_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MemoryStamp")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private suspend fun sharePage(context: Context, page: Page) {
    val file = withContext(Dispatchers.IO) {
        File(context.filesDir, "stamps/share_page.png").also { f -> f.outputStream().use { renderPage(context, page).compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Chia sẻ trang"))
}
