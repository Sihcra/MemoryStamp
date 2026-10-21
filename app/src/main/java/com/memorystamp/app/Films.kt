package com.memorystamp.app

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** A film look: a colour matrix plus the extras it turns on by default. */
class Film(
    val name: String,
    val matrix: ColorMatrix,
    val grain: Boolean = false,
    val vignette: Boolean = false,
    val leak: Boolean = false,
    val date: Boolean = false,
)

/** What the user has picked on the camera screen. Same object drives the preview and the saved photo. */
data class Look(
    val film: Film,
    val strength: Float = 1f,
    val grain: Boolean = film.grain,
    val vignette: Boolean = film.vignette,
    val leak: Boolean = film.leak,
    val date: Boolean = film.date,
)

private fun cm(vararg steps: ColorMatrix) = ColorMatrix().apply { steps.forEach { postConcat(it) } }

private fun scale(r: Float, g: Float, b: Float) = ColorMatrix().apply { setScale(r, g, b, 1f) }

private fun sat(s: Float) = ColorMatrix().apply { setSaturation(s) }

/** Contrast around mid grey plus a brightness lift (lift > 0 raises the blacks, the "faded film" look). */
private fun contrast(c: Float, lift: Float = 0f): ColorMatrix {
    val t = 128f * (1f - c) + lift
    return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
}

private fun shift(r: Float, g: Float, b: Float) =
    ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, r, 0f, 1f, 0f, 0f, g, 0f, 0f, 1f, 0f, b, 0f, 0f, 0f, 1f, 0f))

val FILMS = listOf(
    Film("Gốc", ColorMatrix()),
    Film("Nắng ấm", cm(scale(1.08f, 1.0f, 0.86f), sat(1.1f), contrast(0.92f, 10f)), grain = true),
    Film("Xanh lạnh", cm(scale(0.9f, 1.0f, 1.12f), sat(1.25f), contrast(1.15f))),
    Film("Phố đêm", cm(scale(1.06f, 1.04f, 0.9f), shift(0f, 8f, -4f), contrast(1.12f), sat(1.2f)), grain = true),
    Film("Hoa cúc", cm(scale(1.04f, 1.06f, 0.8f), shift(6f, 12f, 0f), sat(0.9f)), vignette = true, date = true),
    Film("Lấy liền", cm(scale(0.96f, 1.0f, 1.04f), contrast(0.82f, 22f), sat(0.85f)), vignette = true),
    Film("Phai màu", cm(sat(0.6f), contrast(0.8f, 28f)), grain = true),
    Film(
        "Điện ảnh",
        cm(
            ColorMatrix(floatArrayOf(1.15f, -0.05f, -0.1f, 0f, 0f, -0.05f, 1.05f, 0f, 0f, 0f, -0.15f, 0.1f, 1.1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)),
            contrast(1.1f),
        ),
    ),
    Film("Cross", cm(scale(1.2f, 1.1f, 0.7f), shift(0f, 0f, 35f), contrast(1.2f), sat(1.2f)), leak = true),
    Film(
        "Sepia",
        ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
        grain = true,
        vignette = true,
    ),
    Film("Đen trắng hạt", cm(sat(0f), contrast(1.35f)), grain = true),
    Film("Đen trắng mềm", cm(sat(0f), contrast(0.9f, 10f)), grain = true),
)

/** The film's matrix blended with "no change" by [Look.strength]. */
fun Look.toColorFilter(): ColorMatrixColorFilter {
    val m = film.matrix.array
    val id = ColorMatrix().array
    return ColorMatrixColorFilter(ColorMatrix(FloatArray(20) { id[it] + (m[it] - id[it]) * strength }))
}

private val grainTile: Bitmap by lazy {
    val n = 256
    val rnd = Random(7)
    Bitmap.createBitmap(IntArray(n * n) { val v = rnd.nextInt(256); Color.argb(255, v, v, v) }, n, n, Bitmap.Config.ARGB_8888)
}

/** Draws grain, vignette, light leak and date over a w×h area. Used for both the live preview and the saved photo. */
fun Look.drawExtras(canvas: Canvas, w: Float, h: Float, date: LocalDate = LocalDate.now()) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    if (leak) {
        p.shader = LinearGradient(0f, 0f, w * 0.55f, h * 0.3f, Color.argb(150, 255, 120, 40), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }
    if (vignette) {
        p.shader = RadialGradient(w / 2, h / 2, maxOf(w, h) * 0.75f, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p)
    }
    if (grain) {
        // Keep grain size roughly the same on screen and in the 1080px photo.
        p.shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(android.graphics.Matrix().apply { setScale(w / 1080f * 1.5f, w / 1080f * 1.5f) })
        }
        p.alpha = 34
        canvas.drawRect(0f, 0f, w, h, p)
    }
    if (this.date) {
        p.shader = null
        p.alpha = 255
        p.color = Color.rgb(255, 140, 40)
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textSize = w * 0.055f
        p.setShadowLayer(w * 0.01f, 0f, 0f, Color.argb(200, 255, 60, 0))
        val text = date.format(DateTimeFormatter.ofPattern("''yy MM dd"))
        canvas.drawText(text, w - p.measureText(text) - w * 0.06f, h - w * 0.06f, p)
    }
}

/** Applies the whole look to a square photo. */
fun Look.develop(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = toColorFilter() })
    drawExtras(c, src.width.toFloat(), src.height.toFloat())
    return out
}
