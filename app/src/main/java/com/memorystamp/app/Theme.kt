package com.memorystamp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** UI colours. Reading any of them follows [Palette.dark], so flipping it recolours the whole app. */
object Palette {
    var dark by mutableStateOf(false)
    private fun pick(light: Long, night: Long) = Color(if (dark) night else light)

    val Bg get() = pick(0xFFF6F5F2, 0xFF121212)
    val Card get() = pick(0xFFFFFFFF, 0xFF1D1D1C)
    val Soft get() = pick(0xFFECEAE5, 0xFF2A2927)
    val Line get() = pick(0xFFD9D6CF, 0xFF3B3A37)
    val Accent get() = pick(0xFF262420, 0xFFF1EEE8)
    val OnAccent get() = pick(0xFFFFFFFF, 0xFF141414)
    val Ink get() = pick(0xFF262420, 0xFFECE9E3)
    val InkSoft get() = pick(0xFF8A857D, 0xFF9A958D)

    // Fixed pastels for user content (page backgrounds, envelopes); they look the same in both modes.
    val Blush = Color(0xFFF6E1E1)
    val Mint = Color(0xFFDDEDE4)
    val Butter = Color(0xFFF7EBC9)
    val Sky = Color(0xFFDCE7F3)
    val Lilac = Color(0xFFE7E2F3)
    val Paper = Color(0xFFFCFBF7)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(w: Int) = Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))

private fun style(size: Int, weight: Int) = TextStyle(fontFamily = Nunito, fontSize = size.sp, fontWeight = FontWeight(weight))

@Composable
fun CuteTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = (if (Palette.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Palette.Accent,
        onPrimary = Palette.OnAccent,
        primaryContainer = Palette.Soft,
        secondaryContainer = Palette.Soft,
        onSecondaryContainer = Palette.Ink,
        background = Palette.Bg,
        surface = Palette.Bg,
        surfaceVariant = Palette.Soft,
        surfaceContainer = Palette.Card,
        surfaceContainerHigh = Palette.Card,
        surfaceContainerHighest = Palette.Card,
        onSurface = Palette.Ink,
        onBackground = Palette.Ink,
        onSurfaceVariant = Palette.InkSoft,
        outline = Palette.Line,
        outlineVariant = Palette.Soft,
    ),
    typography = Typography(
        headlineSmall = style(24, 800),
        titleLarge = style(20, 800),
        titleMedium = style(16, 700),
        bodyLarge = style(16, 400),
        bodyMedium = style(14, 400),
        bodySmall = style(12, 400),
        labelLarge = style(14, 700),
        labelMedium = style(12, 600),
        labelSmall = style(11, 600),
    ),
    shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp),
    ),
    content = content,
)

/** Round pastel button holding one of our line icons. */
@Composable
fun Bubble(icon: Int, label: String, modifier: Modifier = Modifier, color: Color = Palette.Soft, tint: Color = Palette.Ink, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(color).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** Small rounded label used for chips and tags. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Palette.OnAccent else Palette.Ink,
        modifier = modifier.clip(CircleShape).background(if (selected) Palette.Accent else Palette.Soft)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Gentle tilt per item so stamps look hand-placed. */
fun tiltOf(id: Long) = ((id % 7) - 3) * 1.6f
