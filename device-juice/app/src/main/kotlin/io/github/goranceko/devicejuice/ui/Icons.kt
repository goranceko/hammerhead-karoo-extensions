package io.github.goranceko.devicejuice.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.art.IconPaths
import io.github.goranceko.devicejuice.art.IconSpec
import io.github.goranceko.devicejuice.ui.theme.Palette

/** Line icons from [IconPaths] as Compose vectors, tinted at draw time; band badges. */
object Icons {
    private fun icon(spec: IconSpec): ImageVector =
        ImageVector.Builder(spec.name, 24.dp, 24.dp, 24f, 24f).apply {
            spec.stroke.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            spec.fill.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.White)) }
        }.build()

    private val vectors = mutableMapOf<IconSpec, ImageVector>()

    val groupset: ImageVector get() = of(IconPaths.groupset)

    fun of(spec: IconSpec): ImageVector = vectors.getOrPut(spec) { icon(spec) }

    fun of(r: BatteryReading): ImageVector = of(IconPaths.of(r))

    fun tagOf(r: BatteryReading): String? = IconPaths.tagOf(r)

    /**
     * Band badges in their real colours (the cut-out is drawn in the background colour, so they must not be tinted).
     * Each band has its own shape so colour is never the only cue (PLAN.md 4).
     */
    private fun badge(name: String, color: Color, fill: List<String>, cut: List<String>): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fill.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(color)) }
            cut.forEach {
                addPath(pathData = addPathNodes(it), stroke = SolidColor(Palette.bg), strokeLineWidth = 2.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

    private const val DISC_OK = "M22,12a10,10 0 1,1 -20,0a10,10 0 1,1 20,0z"

    private val badgeCache = mutableMapOf<Pair<Boolean, Band>, ImageVector>()

    private fun buildBadges(): Map<Band, ImageVector> =
        mapOf(
            Band.GREEN to badge("ok", Palette.green, listOf(DISC_OK), listOf("M7 12.5l3.2 3.2L17 9")),
            Band.YELLOW to badge("half", Palette.yellow, listOf("M12 2a10 10 0 0 1 0 20z", "M12,2a10,10 0 0,0 0,20v-2.6a7.4,7.4 0 0,1 0,-14.8z"), emptyList()),
            Band.ORANGE to badge("warn", Palette.orange, listOf("M12 2.5L22.5 21h-21z"), listOf("M12 9v5.5M12 17.6v.4")),
            Band.RED to badge("stop", Palette.red, listOf("M8 2h8l6 6v8l-6 6H8l-6-6V8z"), listOf("M12 6.5v7M12 16.8v.4")),
            Band.GREY to badge("unknown", Palette.grey, listOf(DISC_OK), listOf("M9.3 9.3a2.8 2.8 0 1 1 3.9 2.6c-.8.4-1.2 1-1.2 1.8M12 17v.3")),
        )

    /** Badges are built in their real colours (the cut-outs use the background), one set per palette. */
    fun badge(band: Band): ImageVector {
        val key = Palette.light to band
        return badgeCache[key] ?: buildBadges().also { all -> all.forEach { (b, v) -> badgeCache[Palette.light to b] = v } }.getValue(band)
    }
}
