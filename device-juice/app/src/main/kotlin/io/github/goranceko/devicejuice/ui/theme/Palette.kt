package io.github.goranceko.devicejuice.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.DeviceKind

/** Dark ride-mode palette from mockup.html, slightly lifted for a modern look. */
object Palette {
    val bg = Color(0xFF090B0A)
    val card = Color(0xFF15191A)
    val cardHigh = Color(0xFF1D2224)
    val line = Color(0xFF2A302C)
    val segOff = Color(0xFF2D332F)
    val fg = Color(0xFFF3F5F2)
    val sub = Color(0xFFA3ACA5)
    val faint = Color(0xFF6E7771)

    val green = Color(0xFF3BC46F)
    val yellow = Color(0xFFF2CF2C)
    val orange = Color(0xFFFF8A1F)
    val red = Color(0xFFFF4A3D)
    val grey = Color(0xFF7C857F)

    fun of(band: Band): Color = when (band) {
        Band.GREEN -> green
        Band.YELLOW -> yellow
        Band.ORANGE -> orange
        Band.RED -> red
        Band.GREY -> grey
    }

    /**
     * Fixed accent per sensor type, for the type badge only. Hues stay clear of the battery colours, except radar, which takes a soft Varia red
     * (red, orange, yellow, green), which keep their meaning on the gauge, value and advice.
     */
    /** Solid badge colour for the parts of a multi-part groupset (SRAM AXS). Deeper than the battery red. */
    val axsRed = Color(0xFFD23A3F)

    fun accentOf(kind: DeviceKind): Color = when (kind) {
        DeviceKind.SHIFTING, DeviceKind.SHIFTER_COIN -> Color(0xFF5AA9FF)
        DeviceKind.POWER -> Color(0xFFB08CFF)
        DeviceKind.RADAR -> Color(0xFFD9726E) // soft Varia red, well below the battery red in saturation
        DeviceKind.LIGHT -> Color(0xFFE6E9F0)
        DeviceKind.HR -> Color(0xFFFF7BB5)
        DeviceKind.HEAD_UNIT -> Color(0xFFB6BDB8)
        DeviceKind.OTHER -> Color(0xFF8F98A0)
    }
}
