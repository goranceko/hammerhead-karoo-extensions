package io.github.goranceko.devicejuice.ui

import android.graphics.Typeface
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goranceko.devicejuice.advice.Advice
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.model.ReadingSource
import io.github.goranceko.devicejuice.model.Verdicts
import io.github.goranceko.devicejuice.ui.theme.Palette

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
private val CardShape = RoundedCornerShape(16.dp)

/** Pre-ride battery overview (PLAN.md 3.3, mockup "Before the ride"). Long-press a device to hide it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(
    state: BatteryRepository.State,
    now: Long,
    onHide: (BatteryReading) -> Unit,
    onShowHidden: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    val attention = state.readings.filter { it.band in setOf(Band.RED, Band.ORANGE, Band.YELLOW) }
    val healthy = state.readings.filter { it.band == Band.GREEN }
    val unseen = state.readings.filter { it.band == Band.GREY }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Header(state)
        VerdictCard(state.readings)
        if (attention.isNotEmpty()) {
            SectionLabel("Needs attention", attention.size)
            attention.forEach { AttentionCard(it, now, onHide) }
        }
        if (healthy.isNotEmpty()) {
            SectionLabel("Healthy", healthy.size)
            healthy.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { HealthyTile(it, onHide, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (unseen.isNotEmpty()) {
            SectionLabel("Not seen recently", unseen.size)
            Column(Modifier.fillMaxWidth().clip(CardShape).background(Palette.card)) {
                unseen.forEachIndexed { i, r ->
                    UnseenRow(r, onHide)
                    if (i < unseen.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).background(Palette.line))
                }
            }
        }
        if (state.hiddenCount > 0) {
            Text(
                "Show ${state.hiddenCount} hidden device${if (state.hiddenCount == 1) "" else "s"}",
                color = Palette.sub,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onShowHidden).padding(8.dp),
            )
        }
        Text("Long-press a device to hide it.", color = Palette.faint, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        footer()
    }
}

@Composable
private fun Header(state: BatteryRepository.State) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text("BATTERY CHECK", color = Palette.green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Device Juice", color = Palette.fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed)
        }
        Text("${state.readings.size} devices", color = Palette.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun VerdictCard(readings: List<BatteryReading>) {
    val verdict = Verdicts.of(readings)
    val color = Palette.of(verdict.band)
    val weakest = readings.firstOrNull { it.band != Band.GREY }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.30f), Palette.card)))
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(weakest, verdict.band, 78.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BandBadge(verdict.band, 20.dp)
                Text(weakest?.let(::valueText) ?: "–", color = Palette.fg, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(verdict.title, color = Palette.fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(verdict.detail, color = Palette.sub, fontSize = 13.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Text(
        "${text.uppercase()} · $count",
        color = Palette.sub,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttentionCard(r: BatteryReading, now: Long, onHide: (BatteryReading) -> Unit) {
    val color = Palette.of(r.band)
    val tint = if (r.band == Band.RED) Brush.horizontalGradient(listOf(color.copy(alpha = 0.22f), Palette.card)) else Brush.horizontalGradient(listOf(Palette.cardHigh, Palette.card))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(tint)
            .combinedClickable(onClick = {}, onLongClick = { onHide(r) })
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(r, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, color = Palette.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed, maxLines = 2, lineHeight = 17.sp, overflow = TextOverflow.Ellipsis)
                if (r.partName != null) Text(r.deviceName, color = Palette.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle(r, now), color = Palette.faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BandBadge(r.band, 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(valueText(r), color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Condensed)
                }
                Spacer(Modifier.height(3.dp))
                BatteryGlyph(r, 40.dp, 18.dp)
            }
        }
        Advice.preRide(r)?.let { advice ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(8.dp))
                Text(advice, color = Palette.fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                rangeOf(r.level)?.takeIf { r.percent == null }?.let { Text(it, color = Palette.faint, fontSize = 11.sp) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HealthyTile(r: BatteryReading, onHide: (BatteryReading) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(CardShape)
            .background(Palette.card)
            .combinedClickable(onClick = {}, onLongClick = { onHide(r) })
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(r, 30.dp)
            Spacer(Modifier.weight(1f))
            Text(valueText(r), color = Palette.fg, fontSize = valueSize(r), fontWeight = FontWeight.ExtraBold, fontFamily = Condensed)
        }
        Spacer(Modifier.height(6.dp))
        Text(r.title, color = if (r.enabled) Palette.fg else Palette.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2, lineHeight = 15.sp, overflow = TextOverflow.Ellipsis)
        Text(r.partName?.let { r.deviceName } ?: kindLabel(r.kind), color = Palette.faint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryGlyph(r, 30.dp, 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(r.percent?.let { "exact" } ?: rangeOf(r.level).orEmpty(), color = Palette.faint, fontSize = 10.sp, maxLines = 1)
        }
    }
}

private fun valueSize(r: BatteryReading): TextUnit = if (r.percent != null) 18.sp else 15.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnseenRow(r: BatteryReading, onHide: (BatteryReading) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { onHide(r) })
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(r, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = Palette.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Advice.preRide(r)?.let { Text(it, color = Palette.sub, fontSize = 11.sp, maxLines = 2) }
        }
        Spacer(Modifier.width(6.dp))
        BandBadge(Band.GREY, 18.dp)
    }
}

private fun kindLabel(kind: DeviceKind): String = when (kind) {
    DeviceKind.HEAD_UNIT -> "Head unit"
    DeviceKind.SHIFTING -> "Shifting"
    DeviceKind.SHIFTER_COIN -> "Coin cell"
    DeviceKind.RADAR -> "Radar"
    DeviceKind.LIGHT -> "Light"
    DeviceKind.POWER -> "Power meter"
    DeviceKind.HR -> "Heart rate"
    DeviceKind.OTHER -> "Device"
}

private fun subtitle(r: BatteryReading, now: Long): String {
    val parts = mutableListOf(kindLabel(r.kind))
    if (r.source == ReadingSource.KI2) parts += "via Ki2"
    if (r.source != ReadingSource.KAROO) {
        r.updatedAt?.let { parts += DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString() }
    }
    return parts.joinToString(" · ")
}
