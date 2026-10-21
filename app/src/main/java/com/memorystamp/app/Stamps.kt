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
                dir.listFiles().orEmpty().filter { it.extension == "png" || it.extension == "json" }.forEach { f ->
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
                    if (Regex("""\d+\.(png|json)""").matches(e.name)) {
                        File(dir, e.name).outputStream().use { zip.copyTo(it) }
                        if (e.name.endsWith(".png")) count++
                    }
                }
            }
        }
        load(context)
        return count
    }
}

/** Cuts the centre square of a photo, shrinks it to [size] px. */
fun Bitmap.centerSquare(size: Int = 1080): Bitmap {
    val s = minOf(width, height)
    val square = Bitmap.createBitmap(this, (width - s) / 2, (height - s) / 2, s, s)
    return Bitmap.createScaledBitmap(square, size, size, true)
}

/** Puts the photo on white stamp paper and punches the perforated edge. Holes are transparent in the PNG. */
fun makeStamp(photo: Bitmap): Bitmap {
    val margin = photo.width / 14f
    val size = (photo.width + margin * 2).toInt()
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(Color.rgb(253, 251, 246))
    c.drawBitmap(photo, margin, margin, Paint(Paint.FILTER_BITMAP_FLAG))
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    val r = margin * 0.32f
    val n = 16
    val step = size / n.toFloat()
    for (i in 0..n) {
        val p = i * step
        c.drawCircle(p, 0f, r, hole)
        c.drawCircle(p, size.toFloat(), r, hole)
        c.drawCircle(0f, p, r, hole)
        c.drawCircle(size.toFloat(), p, r, hole)
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
