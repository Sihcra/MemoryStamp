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
import androidx.compose.material3.lightColorScheme
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

object Palette {
    val Cream = Color(0xFFFFF8F3)
    val Paper = Color(0xFFFFFDF8)
    val Pink = Color(0xFFF7B6C6)
    val PinkSoft = Color(0xFFFFE3EA)
    val Rose = Color(0xFFE0718C)
    val Mint = Color(0xFFCBEBDD)
    val Butter = Color(0xFFFFE9A8)
    val Sky = Color(0xFFD4E6FA)
    val Lilac = Color(0xFFE6DDFB)
    val Ink = Color(0xFF5B4B53)
    val InkSoft = Color(0xFFA08F97)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun nunito(w: Int) = Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))

private fun style(size: Int, weight: Int) = TextStyle(fontFamily = Nunito, fontSize = size.sp, fontWeight = FontWeight(weight), color = Palette.Ink)

@Composable
fun CuteTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Palette.Rose,
        onPrimary = Color.White,
        primaryContainer = Palette.PinkSoft,
        secondaryContainer = Palette.PinkSoft,
        onSecondaryContainer = Palette.Ink,
        background = Palette.Cream,
        surface = Palette.Cream,
        surfaceVariant = Palette.PinkSoft,
        surfaceContainer = Palette.Paper,
        surfaceContainerHigh = Palette.Paper,
        surfaceContainerHighest = Palette.Paper,
        onSurface = Palette.Ink,
        onSurfaceVariant = Palette.InkSoft,
        outline = Palette.Pink,
        outlineVariant = Palette.PinkSoft,
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
fun Bubble(icon: Int, label: String, modifier: Modifier = Modifier, color: Color = Palette.PinkSoft, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(color).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, tint = Palette.Ink, modifier = Modifier.size(size * 0.5f)) }
}

/** Small rounded label used for chips and tags. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else Palette.Ink,
        modifier = modifier.clip(CircleShape).background(if (selected) Palette.Rose else Palette.PinkSoft)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Gentle tilt per item so stamps look hand-placed. */
fun tiltOf(id: Long) = ((id % 7) - 3) * 1.6f
