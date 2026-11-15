package io.github.goranceko.devicejuice.field

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import io.github.goranceko.devicejuice.R
import io.github.goranceko.devicejuice.art.FieldArt
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.model.BatteryReading
import io.github.goranceko.devicejuice.model.Values
import io.github.goranceko.devicejuice.ui.caption
import io.github.goranceko.devicejuice.ui.theme.Palette
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Full-page overview field (PLAN.md 3.1 surface 6): the verdict and every device in the profile, so the
 * overview can sit on a ride page and be read before Start. RemoteViews, with the type badges and battery gauges drawn as small bitmaps (art/FieldArt).
 */
class OverviewField(
    private val state: StateFlow<BatteryRepository.State?>,
    private val scope: CoroutineScope,
) : DataTypeImpl("devicejuice", TYPE_ID) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = scope.launch {
            state.filterNotNull().map { Weakest.of(it.readings)?.sortValue }.distinctUntilChanged().collect { v ->
                emitter.onNext(
                    if (v == null) StreamState.Searching
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val heightDp = config.viewSize.second / context.resources.displayMetrics.density
        val job = scope.launch {
            state.collect { s -> emitter.updateView(render(context, s, heightDp)) }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun render(context: Context, s: BatteryRepository.State?, heightDp: Float): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.field_overview)
        val density = context.resources.displayMetrics.density
        v.setInt(R.id.overview_root, "setBackgroundColor", Palette.bg.toArgb())
        val model = s?.let { Overview.of(it.readings, heightDp) }
        v.setTextViewText(R.id.verdict_title, model?.verdict?.title ?: "1Battery Juice")
        v.setTextColor(R.id.verdict_title, (model?.verdict?.band?.let { Palette.of(it) } ?: Palette.fg).toArgb())
        v.setTextViewText(R.id.verdict_detail, model?.verdict?.detail ?: "Loading devices…")
        v.setTextColor(R.id.verdict_detail, Palette.sub.toArgb())
        val header = model == null || model.header
        v.setViewVisibility(R.id.verdict_title, if (header) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.verdict_detail, if (header) View.VISIBLE else View.GONE)
        val card = if (Palette.light) R.drawable.field_card_light else R.drawable.field_card_dark
        val now = System.currentTimeMillis()
        listOf(R.id.card_attention to (model?.attention.orEmpty() to "a"), R.id.card_rest to (model?.rest.orEmpty() to "h"))
            .forEach { (cardId, rowsAndPrefix) ->
                val (rows, prefix) = rowsAndPrefix
                v.setViewVisibility(cardId, if (rows.isEmpty()) View.GONE else View.VISIBLE)
                v.setInt(cardId, "setBackgroundResource", card)
                ids(context, prefix).forEachIndexed { i, ids ->
                    val r = rows.getOrNull(i)
                    v.setViewVisibility(ids.row, if (r == null) View.GONE else View.VISIBLE)
                    if (r != null) fill(v, ids, r, now, density)
                }
            }
        val more = model?.more ?: 0
        v.setViewVisibility(R.id.more, if (more > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.more, "+$more more · open 1Battery Juice")
        v.setTextColor(R.id.more, Palette.sub.toArgb())
        return v
    }

    /** One row like the app's compact list: type badge, name over type and time left, value in the battery colour over its gauge. */
    private fun fill(v: RemoteViews, ids: RowIds, r: BatteryReading, now: Long, density: Float) {
        fun px(dp: Int) = (dp * density).toInt()
        v.setImageViewBitmap(ids.badge, FieldArt.badge(r, px(22)))
        v.setTextViewText(ids.name, r.title)
        v.setTextColor(ids.name, (if (r.stale) Palette.faint else Palette.fg).toArgb())
        v.setTextViewText(ids.caption, caption(r, now))
        v.setTextColor(ids.caption, Palette.faint.toArgb())
        v.setTextViewText(ids.value, Values.text(r))
        v.setTextColor(ids.value, Palette.of(r.band).toArgb())
        v.setImageViewBitmap(ids.gauge, FieldArt.gauge(r, px(26), px(11)))
    }

    private class RowIds(val row: Int, val badge: Int, val name: Int, val caption: Int, val value: Int, val gauge: Int)

    private val idCache = mutableMapOf<String, List<RowIds>>()

    /** Row view ids of one card ("a" attention, "h" the rest), looked up once by name: field_overview.xml has 12 of each. */
    private fun ids(context: Context, prefix: String): List<RowIds> = idCache.getOrPut(prefix) {
        fun id(name: String) = context.resources.getIdentifier(name, "id", context.packageName)
        (0 until Overview.MAX_ROWS).map { i ->
            RowIds(id("${prefix}row$i"), id("${prefix}badge$i"), id("${prefix}name$i"), id("${prefix}cap$i"), id("${prefix}value$i"), id("${prefix}gauge$i"))
        }
    }

    companion object {
        const val TYPE_ID = "overview"
    }
}
