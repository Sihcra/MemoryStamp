package com.memorystamp.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One saved stamp. The id is the capture time in millis; the PNG and a small JSON sit side by side in filesDir/stamps. */
data class Stamp(val id: Long, val title: String, val note: String, val film: String, val image: File) {
    val day: LocalDate get() = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** Everything is stored on the phone; nothing leaves it unless the user shares or exports. */
private val BACKUP_NAME = Regex("""\d+\.(png|json)""")

object Stamps {
    val all = mutableStateListOf<Stamp>()
    private lateinit var dir: File

    fun load(context: Context) {
        dir = File(context.filesDir, "stamps").apply { mkdirs() }
        val list = dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching {
                val j = JSONObject(f.readText())
                Stamp(f.nameWithoutExtension.toLong(), j.optString("title"), j.optString("note"), j.optString("film"), File(dir, "${f.nameWithoutExtension}.png"))
            }.getOrNull()?.takeIf { it.image.exists() }
        }
        all.clear()
        all.addAll(list.sortedByDescending { it.id })
    }

    fun add(stampBitmap: Bitmap, film: String): Stamp {
        val id = System.currentTimeMillis()
        val png = File(dir, "$id.png")
        png.outputStream().use { stampBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Stamp(id, "", "", film, png).also { writeMeta(it); all.add(0, it) }
    }

    fun update(stamp: Stamp) {
        writeMeta(stamp)
        all.replaceAll { if (it.id == stamp.id) stamp else it }
    }

    fun delete(stamp: Stamp) {
        stamp.image.delete()
        File(dir, "${stamp.id}.json").delete()
        all.removeAll { it.id == stamp.id }
    }

    private fun writeMeta(s: Stamp) {
        val json = JSONObject().put("title", s.title).put("note", s.note).put("film", s.film).toString()
        val tmp = File(dir, "${s.id}.json.tmp")
        tmp.writeText(json)
        tmp.renameTo(File(dir, "${s.id}.json"))
    }

    /** Extra copy in Pictures/MemoryStamp so it shows up in the phone's gallery and survives uninstalling. */
    fun copyToGallery(context: Context, stamp: Stamp) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "MemoryStamp_${stamp.id}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MemoryStamp")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> stamp.image.inputStream().use { it.copyTo(out) } }
    }

    fun exportZip(context: Context, target: Uri) {
        context.contentResolver.openOutputStream(target)?.use { raw ->
            ZipOutputStream(raw).use { zip ->
                val extras = listOf("pages.json", "letters.json").map { File(context.filesDir, it) }.filter { it.exists() }
                (dir.listFiles().orEmpty().filter { BACKUP_NAME.matches(it.name) } + extras).forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Restores a backup made by [exportZip]. Only flat "<number>.png/json" entries are accepted. */
    fun importZip(context: Context, source: Uri): Int {
        var count = 0
        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                generateSequence { zip.nextEntry }.forEach { e ->
                    if (BACKUP_NAME.matches(e.name)) {
                        File(dir, e.name).outputStream().use { zip.copyTo(it) }
                        if (e.name.endsWith(".png")) count++
                    } else if (e.name == "pages.json" || e.name == "letters.json") {
                        // ponytail: a restore replaces the scrapbook and letters instead of merging them.
                        File(context.filesDir, e.name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        load(context)
        Pages.load(context)
        Letters.load(context)
        Letters.all.forEach { Letters.schedule(context, it) }
        return count
    }
}

/** Stamp photos are portrait 3:4. */
const val STAMP_ASPECT = 3f / 4f

/**
 * Cuts the part of the photo that sat under the stamp window on screen. The preview fills a viewW×viewH box
 * (centre crop) and the window is winW×winH in the middle of it. Without a view, takes the largest centred 3:4.
 */
fun Bitmap.cropStamp(viewW: Float = 0f, viewH: Float = 0f, winW: Float = 0f, outW: Int = 900): Bitmap {
    var cw: Float
    var ch: Float
    if (viewW > 0 && viewH > 0) {
        val scale = maxOf(viewW / width, viewH / height)
        cw = winW / scale
        ch = cw / STAMP_ASPECT
    } else {
        cw = minOf(width.toFloat(), height * STAMP_ASPECT)
        ch = cw / STAMP_ASPECT
    }
    cw = cw.coerceAtMost(width.toFloat())
    ch = ch.coerceAtMost(height.toFloat())
    val crop = Bitmap.createBitmap(this, ((width - cw) / 2).toInt(), ((height - ch) / 2).toInt(), cw.toInt(), ch.toInt())
    return Bitmap.createScaledBitmap(crop, outW, (outW / STAMP_ASPECT).toInt(), true)
}

/** Puts the photo on white stamp paper and punches the perforated edge. Holes are transparent in the PNG. */
fun makeStamp(photo: Bitmap): Bitmap {
    val margin = photo.width / 14f
    val w = photo.width + margin * 2
    val h = photo.height + margin * 2
    val out = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(Color.rgb(253, 252, 248))
    c.drawBitmap(photo, margin, margin, Paint(Paint.FILTER_BITMAP_FLAG))
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    val r = margin * 0.32f
    val target = margin * 0.95f
    val nx = (w / target).toInt()
    val ny = (h / target).toInt()
    for (i in 0..nx) {
        val x = i * w / nx
        c.drawCircle(x, 0f, r, hole)
        c.drawCircle(x, h, r, hole)
    }
    for (i in 0..ny) {
        val y = i * h / ny
        c.drawCircle(0f, y, r, hole)
        c.drawCircle(w, y, r, hole)
    }
    return out
}

private val thumbCache = android.util.LruCache<String, Bitmap>(80)

/** Small decoded copy for grids; cached in memory. Call off the main thread. */
fun loadThumb(file: File, px: Int): Bitmap? {
    val key = "${file.path}@$px"
    thumbCache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= px) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    thumbCache.put(key, bmp)
    return bmp
}
