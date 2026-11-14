package io.github.goranceko.devicejuice.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.goranceko.devicejuice.model.Band
import io.github.goranceko.devicejuice.model.DeviceKind

/**
 * Ride-mode palettes: dark (default, from mockup.html) and light for bright sun.
 * [light] is Compose state, so switching it recomposes every screen that reads a colour.
 */
object Palette {
    var light by mutableStateOf(false)

    private fun pick(dark: Long, light: Long) = Color(if (this.light) light else dark)

    val bg get() = pick(0xFF090B0A, 0xFFF2F4F1)
    val card get() = pick(0xFF15191A, 0xFFFFFFFF)
    val cardHigh get() = pick(0xFF1D2224, 0xFFE9EDEA)
    val line get() = pick(0xFF2A302C, 0xFFDDE2DE)
    val segOff get() = pick(0xFF2D332F, 0xFFD3D9D5)
    val fg get() = pick(0xFFF3F5F2, 0xFF101412)
    val sub get() = pick(0xFFA3ACA5, 0xFF46504A)
    val faint get() = pick(0xFF6E7771, 0xFF737C76)

    /** Icon colour on solid badges: black in both modes. */
    val ink = Color(0xFF090B0A)

    val green get() = pick(0xFF3BC46F, 0xFF1A9A4D)
    val yellow get() = pick(0xFFF2CF2C, 0xFFC29400)
    val orange get() = pick(0xFFFF8A1F, 0xFFE06800)
    val red get() = pick(0xFFFF4A3D, 0xFFD92E26)
    val grey get() = pick(0xFF7C857F, 0xFF8A938D)

    fun of(band: Band): Color = when (band) {
        Band.GREEN -> green
        Band.YELLOW -> yellow
        Band.ORANGE -> orange
        Band.RED -> red
        Band.GREY -> grey
    }

    /** Solid badge colour for the parts of a multi-part groupset (SRAM AXS). Deeper than the battery red. */
    val axsRed = Color(0xFFD23A3F)

    /**
     * Fixed accent per sensor type, for the type badge only. Hues stay clear of the battery colours,
     * except radar, which takes a soft Varia red. Battery colour keeps its meaning on the gauge, value and advice.
     */
    fun accentOf(kind: DeviceKind): Color = when (kind) {
        DeviceKind.SHIFTING, DeviceKind.SHIFTER_COIN -> pick(0xFF5AA9FF, 0xFF2F7FE0)
        DeviceKind.POWER -> pick(0xFFB08CFF, 0xFF7650DC)
        DeviceKind.RADAR -> pick(0xFFD9726E, 0xFFC24F4A) // soft Varia red, well below the battery red in saturation
        DeviceKind.LIGHT -> pick(0xFFE6E9F0, 0xFF5F6B7A)
        DeviceKind.HR -> pick(0xFFFF7BB5, 0xFFDA4A8E)
        DeviceKind.HEAD_UNIT -> pick(0xFFB6BDB8, 0xFF6E7771)
        DeviceKind.OTHER -> pick(0xFF8F98A0, 0xFF6E7771)
    }
}
