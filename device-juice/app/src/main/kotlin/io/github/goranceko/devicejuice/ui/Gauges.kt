package io.github.goranceko.devicejuice.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.ui.theme.Palette
import io.hammerhead.karooext.models.BatteryStatus

/** Coarse levels are 5 segments (PLAN.md 4): CRITICAL 1 … NEW 5, INVALID 0. */
fun segmentsOf(level: BatteryStatus?): Int = when (level) {
    BatteryStatus.NEW -> 5
    BatteryStatus.GOOD -> 4
    BatteryStatus.OK -> 3
    BatteryStatus.LOW -> 2
    BatteryStatus.CRITICAL -> 1
    BatteryStatus.INVALID, null -> 0
}

/** The % range a Karoo level stands for (BatteryStatus.fromPercentage). */
fun rangeOf(level: BatteryStatus?): String? = when (level) {
    BatteryStatus.NEW -> "96–100 %"
    BatteryStatus.GOOD -> "81–95 %"
    BatteryStatus.OK -> "46–80 %"
    BatteryStatus.LOW -> "16–45 %"
    BatteryStatus.CRITICAL -> "1–15 %"
    BatteryStatus.INVALID, null -> null
}

fun valueText(r: BatteryReading): String = r.percent?.let { "$it%" } ?: when (r.level) {
    null, BatteryStatus.INVALID -> "–"
    BatteryStatus.NEW -> "Full"
    BatteryStatus.GOOD -> "Good"
    BatteryStatus.OK -> "OK"
    BatteryStatus.LOW -> "Low"
    BatteryStatus.CRITICAL -> "Critical"
}

/** Short age for tight layouts: "just now", "42m ago", "3h ago", "5d ago", then "2 Jun". */
fun ageText(updatedAt: Long?, now: Long, date: (Long) -> String = { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(it)) }): String? {
    if (updatedAt == null) return null
    val min = (now - updatedAt).coerceAtLeast(0) / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "${min}m ago"
        min < 24 * 60 -> "${min / 60}h ago"
        min < 7 * 24 * 60 -> "${min / (24 * 60)}d ago"
        else -> date(updatedAt)
    }
}

/** Horizontal battery: continuous fill for an exact %, 5 cells for a level. */
@Composable
fun BatteryGlyph(r: BatteryReading, width: Dp = 34.dp, height: Dp = 16.dp) {
    val color = Palette.of(r.band)
    Canvas(Modifier.size(width, height)) {
        val nub = size.width * 0.08f
        val stroke = 2.dp.toPx()
        val body = Size(size.width - nub - stroke, size.height - stroke)
        val o = Offset(stroke / 2, stroke / 2)
        drawRoundRect(color.copy(alpha = 0.9f), o, body, CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        drawRoundRect(color.copy(alpha = 0.9f), Offset(size.width - nub, size.height * 0.3f), Size(nub, size.height * 0.4f), CornerRadius(1.5.dp.toPx()))
        val pad = stroke + 1.5.dp.toPx()
        val inner = Size(body.width - 2 * pad + stroke, body.height - 2 * pad + stroke)
        val start = Offset(pad, pad)
        if (r.percent != null) {
            val w = inner.width * (r.percent.coerceIn(0, 100) / 100f)
            drawRoundRect(color, start, Size(w.coerceAtLeast(2f), inner.height), CornerRadius(1.5.dp.toPx()))
        } else {
            val lit = if (r.stale) 0 else segmentsOf(r.level)
            val gap = 1.5.dp.toPx()
            val cell = (inner.width - gap * 4) / 5
            repeat(5) { i ->
                drawRoundRect(
                    if (i < lit) color else Palette.segOff,
                    Offset(start.x + i * (cell + gap), start.y),
                    Size(cell, inner.height),
                    CornerRadius(1.dp.toPx()),
                )
            }
        }
    }
}

/** Ring gauge for the verdict: continuous arc for an exact %, 5 arc segments for a level. */
@Composable
fun LevelRing(r: BatteryReading?, band: Band, size: Dp, content: @Composable () -> Unit) {
    val color = Palette.of(band)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 7.dp.toPx()
            val arcSize = Size(this.size.width - w, this.size.height - w)
            val tl = Offset(w / 2, w / 2)
            if (r?.percent != null) {
                drawArc(Palette.segOff, -90f, 360f, false, tl, arcSize, style = Stroke(w))
                drawArc(color, -90f, 360f * r.percent.coerceIn(0, 100) / 100f, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            } else {
                val lit = if (r == null || r.stale) 0 else segmentsOf(r.level)
                val sweep = 360f / 5
                repeat(5) { i ->
                    drawArc(if (i < lit) color else Palette.segOff, -90f + i * sweep + 4f, sweep - 8f, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

/** Device-type icon on a soft circle, both in the band colour (like Karoo's own Sensors list), with an L/R tag for paired parts. */
@Composable
fun KindBadge(r: BatteryReading, size: Dp = 40.dp) {
    val color = Palette.accentOf(r.kind)
    val icon = Icons.of(r)
    // A whole electronic groupset (Di2) gets a solid badge so it stands out from its own parts and the AXS rows.
    val solid = icon == Icons.groupset
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).background(if (solid) color else color.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = r.kind.name, tint = if (solid) Palette.bg else color, modifier = Modifier.size(size * 0.62f))
        }
        Icons.tagOf(r)?.let { tag ->
            val chip = size * 0.44f
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = chip * 0.18f, y = chip * 0.12f)
                    .size(chip)
                    .border(1.5.dp, Palette.bg, CircleShape)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(tag, color = Palette.bg, fontSize = (chip.value * 0.62f).sp, fontWeight = FontWeight.ExtraBold, lineHeight = (chip.value * 0.62f).sp)
            }
        }
    }
}

@Composable
fun BandBadge(band: Band, size: Dp = 22.dp) {
    Image(Icons.badge(band), contentDescription = band.name, modifier = Modifier.size(size))
}

/** Small push-pin after a pinned device's name. */
@Composable
fun PinMark(size: Dp = 12.dp) {
    Canvas(Modifier.padding(start = 4.dp).size(size)) {
        val w = this.size.width
        val c = Palette.sub
        rotate(45f) {
            drawRoundRect(c, topLeft = Offset(w * 0.3f, w * 0.05f), size = Size(w * 0.4f, w * 0.35f), cornerRadius = CornerRadius(w * 0.08f))
            drawLine(c, Offset(w * 0.15f, w * 0.42f), Offset(w * 0.85f, w * 0.42f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
            drawLine(c, Offset(w * 0.5f, w * 0.45f), Offset(w * 0.5f, w * 0.98f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        }
    }
}
