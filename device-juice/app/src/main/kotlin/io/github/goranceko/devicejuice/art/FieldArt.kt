package io.github.goranceko.devicejuice.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.ui.segmentsOf
import io.github.goranceko.devicejuice.ui.theme.Palette

/**
 * The app's type badge and battery gauge (ui/Gauges.kt) drawn as small bitmaps for the ride-page fields, which are
 * RemoteViews and can't run Compose. Cached by what they show; a badge is a few KB, well under the binder limit.
 */
object FieldArt {
    private val cache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 64
    }
    private val paths = mutableMapOf<String, Path>()

    private fun path(d: String): Path = paths.getOrPut(d) { PathParser.createPathFromPathData(d) }

    /** Type icon on a soft circle in the type colour; solid for groupsets; L/R chip for paired parts. */
    @Synchronized
    fun badge(r: BatteryReading, px: Int): Bitmap {
        val spec = IconPaths.of(r)
        val solid = IconPaths.solid(r)
        val color = (if (IconPaths.groupsetPart(r)) Palette.axsRed else Palette.accentOf(r.kind)).toArgb()
        val tag = IconPaths.tagOf(r)
        val key = "badge:${spec.name}:$solid:$color:$tag:$px:${Palette.light}"
        return cache.getOrPut(key) {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val d = px * if (tag != null) 0.9f else 1f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (solid) color else withAlpha(color, 0.16f) }
            c.drawCircle(d / 2, d / 2, d / 2, fill)
            val iconColor = if (solid) Palette.ink.toArgb() else color
            val s = d * 0.62f / 24f
            val m = Matrix().apply { setScale(s, s); postTranslate((d - 24 * s) / 2, (d - 24 * s) / 2) }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2.2f * s; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = iconColor
            }
            val solidFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = iconColor }
            spec.stroke.forEach { c.drawPath(Path(path(it)).apply { transform(m) }, stroke) }
            spec.fill.forEach { c.drawPath(Path(path(it)).apply { transform(m) }, solidFill) }
            if (tag != null) {
                val chip = px * 0.46f
                val cx = px - chip / 2
                val cy = px - chip / 2
                c.drawCircle(cx, cy, chip / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Palette.card.toArgb() })
                c.drawCircle(cx, cy, chip / 2 - px * 0.04f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = if (solid) Palette.fg.toArgb() else color })
                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Palette.bg.toArgb(); textSize = chip * 0.62f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
                }
                c.drawText(tag, cx, cy - (text.descent() + text.ascent()) / 2, text)
            }
            bmp
        }
    }

    /** Horizontal battery in the band colour: continuous fill for an exact %, 5 cells for a level. */
    @Synchronized
    fun gauge(r: BatteryReading, w: Int, h: Int): Bitmap {
        val color = Palette.of(r.band).toArgb()
        val lit = if (r.stale) 0 else segmentsOf(r.level)
        val key = "gauge:$color:${r.percent}:$lit:$w:$h:${Palette.light}"
        return cache.getOrPut(key) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val stroke = h * 0.125f
            val nub = w * 0.08f
            val body = RectF(stroke / 2, stroke / 2, w - nub - stroke / 2, h - stroke / 2)
            val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; this.color = withAlpha(color, 0.9f) }
            val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Palette.segOff.toArgb() }
            c.drawRoundRect(body, h * 0.19f, h * 0.19f, outline)
            c.drawRoundRect(RectF(w - nub, h * 0.3f, w.toFloat(), h * 0.7f), h * 0.1f, h * 0.1f, solid)
            val pad = stroke + h * 0.09f
            val inner = RectF(pad, pad, body.right - pad + stroke / 2, h - pad)
            val r2 = h * 0.06f
            if (r.percent != null) {
                val fw = (inner.width() * r.percent.coerceIn(0, 100) / 100f).coerceAtLeast(2f)
                c.drawRoundRect(RectF(inner.left, inner.top, inner.left + fw, inner.bottom), r2, r2, solid)
            } else {
                val gap = h * 0.09f
                val cell = (inner.width() - gap * 4) / 5
                repeat(5) { i ->
                    val x = inner.left + i * (cell + gap)
                    c.drawRoundRect(RectF(x, inner.top, x + cell, inner.bottom), r2, r2, if (i < lit) solid else off)
                }
            }
            bmp
        }
    }

    private fun withAlpha(argb: Int, a: Float): Int = (argb and 0x00FFFFFF) or ((255 * a).toInt() shl 24)
}
