package io.github.goranceko.devicejuice.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.goranceko.devicejuice.model.Band

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
}
