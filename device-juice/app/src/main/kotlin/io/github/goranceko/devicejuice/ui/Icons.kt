package io.github.goranceko.devicejuice.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.DeviceKind
import io.github.goranceko.devicejuice.ui.theme.Palette

/** Line icons on a 24-unit grid (from mockup.html), tinted at draw time. */
object Icons {
    private fun icon(name: String, stroke: List<String>, fill: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            stroke.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2.2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            fill.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.White)) }
        }.build()

    val shifting = icon(
        "shifting",
        listOf("M19,12a7,7 0 1,1 -14,0a7,7 0 1,1 14,0", "M12 2v3M12 19v3M2 12h3M19 12h3"),
        listOf("M14.2,12a2.2,2.2 0 1,1 -4.4,0a2.2,2.2 0 1,1 4.4,0"),
    )
    val shifter = icon("shifter", listOf("M8 3h6l2 6-3 12h-3L9 9z"))
    val radar = icon("radar", listOf("M10 9h4a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M6 6a8 8 0 0 1 12 0M3.5 3.5a12 12 0 0 1 17 0"))
    val light = icon("light", listOf("M9 18h6M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9V16h7v-2.1A6 6 0 0 0 12 3z"))
    val power = icon("power", listOf("M5 8h9a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2z", "M16 12h5"))
    val heart = icon("heart", listOf("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"))
    val karoo = icon("karoo", listOf("M8.5 2.5h7a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-7a2.5 2.5 0 0 1-2.5-2.5v-14a2.5 2.5 0 0 1 2.5-2.5z", "M9 18h6"))
    val other = icon("other", listOf("M7 6h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M10 3v3M14 3v3"))

    fun of(kind: DeviceKind): ImageVector = when (kind) {
        DeviceKind.HEAD_UNIT -> karoo
        DeviceKind.SHIFTING -> shifting
        DeviceKind.SHIFTER_COIN -> shifter
        DeviceKind.RADAR -> radar
        DeviceKind.LIGHT -> light
        DeviceKind.POWER -> power
        DeviceKind.HR -> heart
        DeviceKind.OTHER -> other
    }

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

    private val badges: Map<Band, ImageVector> by lazy {
        mapOf(
            Band.GREEN to badge("ok", Palette.green, listOf(DISC_OK), listOf("M7 12.5l3.2 3.2L17 9")),
            Band.YELLOW to badge("half", Palette.yellow, listOf("M12 2a10 10 0 0 1 0 20z", "M12,2a10,10 0 0,0 0,20v-2.6a7.4,7.4 0 0,1 0,-14.8z"), emptyList()),
            Band.ORANGE to badge("warn", Palette.orange, listOf("M12 2.5L22.5 21h-21z"), listOf("M12 9v5.5M12 17.6v.4")),
            Band.RED to badge("stop", Palette.red, listOf("M8 2h8l6 6v8l-6 6H8l-6-6V8z"), listOf("M12 6.5v7M12 16.8v.4")),
            Band.GREY to badge("unknown", Palette.grey, listOf(DISC_OK), listOf("M9.3 9.3a2.8 2.8 0 1 1 3.9 2.6c-.8.4-1.2 1-1.2 1.8M12 17v.3")),
        )
    }

    fun badge(band: Band): ImageVector = badges.getValue(band)
}
