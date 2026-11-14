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

    /** Chainring: toothed ring around the spider. Front derailleur and whole-groupset devices (Di2 via Ki2). */
    val chainring = icon(
        "chainring",
        listOf(
            "M18.5,12a6.5,6.5 0 1,1 -13,0a6.5,6.5 0 1,1 13,0",
            "M14.5,12a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0",
            "M12 2.8v2.7M12 18.5v2.7M2.8 12h2.7M18.5 12h2.7M5.5 5.5l1.9 1.9M16.6 16.6l1.9 1.9M5.5 18.5l1.9-1.9M16.6 7.4l1.9-1.9",
        ),
    )

    /** Electronic groupset (Di2 via Ki2, a shifting system shown as one device): toothed ring with a lightning bolt. */
    val groupset = icon(
        "groupset",
        listOf(
            "M19.5,12a7.5,7.5 0 1,1 -15,0a7.5,7.5 0 1,1 15,0",
            "M12 2.2v2.3M12 19.5v2.3M2.2 12h2.3M19.5 12h2.3M5.1 5.1l1.6 1.6M17.3 17.3l1.6 1.6M5.1 18.9l1.6-1.6M17.3 6.7l1.6-1.6",
        ),
        listOf("M13.2 6.8L8.9 12.9h2.9l-1 4.3 4.3-6.1h-2.9z"),
    )

    /** Cassette seen from the side: cogs getting smaller, for the rear derailleur. */
    val cassette = icon("cassette", listOf("M4.5 3v18M9 5v14M13.5 7v10M18 9v6", "M2.5 21h19"))

    val shifter = icon("shifter", listOf("M8 3h6l2 6-3 12h-3L9 9z"))
    val radar = icon("radar", listOf("M10 9h4a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M6 6a8 8 0 0 1 12 0M3.5 3.5a12 12 0 0 1 17 0"))
    val light = icon("light", listOf("M9 18h6M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9V16h7v-2.1A6 6 0 0 0 12 3z"))

    /** Crank arm from the bottom bracket to a pedal: power meters (pedals, cranks, spiders). */
    val crank = icon(
        "crank",
        listOf("M9.5,6.5a3,3 0 1,1 -6,0a3,3 0 1,1 6,0", "M8.6 8.6l7.6 7.6", "M13.2 21l7.8-7.8"),
        listOf("M7.5,6.5a1,1 0 1,1 -2,0a1,1 0 1,1 2,0"),
    )
    val heart = icon("heart", listOf("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"))
    val karoo = icon("karoo", listOf("M8.5 2.5h7a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5h-7a2.5 2.5 0 0 1-2.5-2.5v-14a2.5 2.5 0 0 1 2.5-2.5z", "M9 18h6"))
    val other = icon("other", listOf("M7 6h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M10 3v3M14 3v3"))

    fun of(r: BatteryReading): ImageVector {
        val key = r.componentKey.orEmpty()
        return when {
            key.contains("REAR_DERAILLEUR") -> cassette
            key.contains("FRONT_DERAILLEUR") -> chainring
            else -> when (r.kind) {
                DeviceKind.HEAD_UNIT -> karoo
                DeviceKind.SHIFTING -> groupset
                DeviceKind.SHIFTER_COIN -> shifter
                DeviceKind.RADAR -> radar
                DeviceKind.LIGHT -> light
                DeviceKind.POWER -> crank
                DeviceKind.HR -> heart
                DeviceKind.OTHER -> other
            }
        }
    }

    /** Corner tag for parts that come in pairs: "L" / "R" for shifters and left/right power components. */
    fun tagOf(r: BatteryReading): String? {
        val key = r.componentKey ?: return null
        return when {
            key.startsWith("LEFT") -> "L"
            key.startsWith("RIGHT") -> "R"
            else -> null
        }
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
