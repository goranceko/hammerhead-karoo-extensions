package io.github.goranceko.devicejuice.field

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import io.github.goranceko.devicejuice.R
import io.github.goranceko.devicejuice.data.BatteryRepository
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
 * overview can sit on a ride page and be read before Start. Plain RemoteViews (no bitmap, so no binder size limit).
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
        val slots = Overview.rowsThatFit(config.viewSize.second / context.resources.displayMetrics.density)
        val job = scope.launch {
            state.collect { s -> emitter.updateView(render(context, s, slots)) }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun render(context: Context, s: BatteryRepository.State?, slots: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.field_overview)
        v.setInt(R.id.overview_root, "setBackgroundColor", Palette.bg.toArgb())
        val model = s?.let { Overview.of(it.readings, slots) }
        val title = model?.verdict?.title ?: "Device Juice"
        v.setTextViewText(R.id.verdict_title, title)
        v.setTextColor(R.id.verdict_title, (model?.verdict?.band?.let { Palette.of(it) } ?: Palette.fg).toArgb())
        v.setTextViewText(R.id.verdict_detail, model?.verdict?.detail ?: "Loading devices…")
        v.setTextColor(R.id.verdict_detail, Palette.sub.toArgb())
        v.setInt(R.id.verdict_rule, "setBackgroundColor", Palette.line.toArgb())
        val rows = model?.rows.orEmpty()
        ROWS.forEachIndexed { i, ids ->
            val r = rows.getOrNull(i)
            if (r == null) {
                v.setViewVisibility(ids.row, View.GONE)
                return@forEachIndexed
            }
            v.setViewVisibility(ids.row, View.VISIBLE)
            v.setTextColor(ids.dot, Palette.accentOf(r.kind).toArgb())
            v.setTextViewText(ids.name, Overview.label(r))
            v.setTextColor(ids.name, (if (r.stale) Palette.faint else Palette.fg).toArgb())
            v.setTextViewText(ids.value, Weakest.value(r))
            v.setTextColor(ids.value, Palette.of(r.band).toArgb())
        }
        val more = model?.more ?: 0
        v.setViewVisibility(R.id.more, if (more > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.more, "+$more more · open Device Juice")
        v.setTextColor(R.id.more, Palette.sub.toArgb())
        return v
    }

    private class RowIds(val row: Int, val dot: Int, val name: Int, val value: Int)

    companion object {
        const val TYPE_ID = "overview"

        private val ROWS = listOf(
            RowIds(R.id.row0, R.id.dot0, R.id.name0, R.id.value0),
            RowIds(R.id.row1, R.id.dot1, R.id.name1, R.id.value1),
            RowIds(R.id.row2, R.id.dot2, R.id.name2, R.id.value2),
            RowIds(R.id.row3, R.id.dot3, R.id.name3, R.id.value3),
            RowIds(R.id.row4, R.id.dot4, R.id.name4, R.id.value4),
            RowIds(R.id.row5, R.id.dot5, R.id.name5, R.id.value5),
            RowIds(R.id.row6, R.id.dot6, R.id.name6, R.id.value6),
            RowIds(R.id.row7, R.id.dot7, R.id.name7, R.id.value7),
            RowIds(R.id.row8, R.id.dot8, R.id.name8, R.id.value8),
            RowIds(R.id.row9, R.id.dot9, R.id.name9, R.id.value9),
            RowIds(R.id.row10, R.id.dot10, R.id.name10, R.id.value10),
            RowIds(R.id.row11, R.id.dot11, R.id.name11, R.id.value11),
        )
    }
}
