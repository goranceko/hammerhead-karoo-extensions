package io.github.goranceko.devicejuice.field

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import io.github.goranceko.devicejuice.R
import io.github.goranceko.devicejuice.data.BatteryRepository
import io.github.goranceko.devicejuice.model.Band
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * In-ride fields (milestone 6), drawn with RemoteViews:
 * - [WEAKEST]: the emptiest device, coloured by its battery band.
 * - [STRIP]: blank while all is fine; shows the weakest device once something is red or orange.
 */
class BatteryField(
    private val fieldId: String,
    private val state: StateFlow<BatteryRepository.State?>,
    private val scope: CoroutineScope,
) : DataTypeImpl("devicejuice", fieldId) {

    /** Numeric stream: the weakest device's value (exact % or level midpoint), so Karoo sees the field as live. */
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
        val job = scope.launch {
            state.collect { s -> emitter.updateView(render(context, s, config)) }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun render(context: Context, s: BatteryRepository.State?, config: ViewConfig): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.field_battery)
        val readings = s?.readings.orEmpty()
        val w = Weakest.of(readings)
        val strip = fieldId == STRIP
        val text = if (strip) Weakest.stripText(readings) else w?.let { Weakest.value(it) }
        if (w == null || text == null) {
            // Strip stays blank while all is fine; the 1×1 waits for its first reading.
            views.setViewVisibility(R.id.field_root, if (strip) View.INVISIBLE else View.VISIBLE)
            views.setTextViewText(R.id.field_title, "Batteries")
            views.setTextViewText(R.id.field_value, if (s == null) "…" else "–")
            views.setInt(R.id.field_root, "setBackgroundColor", Palette.grey.toArgb())
            return views
        }
        views.setViewVisibility(R.id.field_root, View.VISIBLE)
        views.setInt(R.id.field_root, "setBackgroundColor", Palette.of(w.band).toArgb())
        views.setTextColor(R.id.field_title, Palette.ink.toArgb())
        views.setTextColor(R.id.field_value, Palette.ink.toArgb())
        if (strip) {
            views.setViewVisibility(R.id.field_title, View.GONE)
            views.setTextViewText(R.id.field_value, text)
        } else {
            views.setViewVisibility(R.id.field_title, View.VISIBLE)
            views.setTextViewText(R.id.field_title, if (w.band == Band.GREEN) "All batteries OK" else w.title)
            views.setTextViewText(R.id.field_value, text)
        }
        return views
    }

    companion object {
        const val WEAKEST = "weakest"
        const val STRIP = "strip"
    }
}
