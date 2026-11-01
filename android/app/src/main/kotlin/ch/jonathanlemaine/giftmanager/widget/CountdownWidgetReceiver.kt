package ch.jonathanlemaine.giftmanager.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import java.time.Duration

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Premier widget posé : rafraîchissement immédiat, puis la cadence habituelle.
        WidgetScheduler.schedule(context, Duration.ZERO)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancel(context)
    }
}
