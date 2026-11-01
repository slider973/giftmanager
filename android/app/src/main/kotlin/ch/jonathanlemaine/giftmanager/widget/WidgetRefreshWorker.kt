package ch.jonathanlemaine.giftmanager.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ch.jonathanlemaine.giftmanager.GiftManagerApp
import ch.jonathanlemaine.giftmanager.core.session.SessionPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Recharge les événements puis redessine le widget ; se replanifie tant qu'un widget est posé (#80). */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val session = (applicationContext as GiftManagerApp).container.session
        // Process froid : la session se charge encore, on lui laisse un peu de temps.
        val state = withTimeoutOrNull(SESSION_WAIT_MILLIS) { session.state.first { it.phase != SessionPhase.Launching } }
        if (state?.phase == SessionPhase.Ready) session.refreshGroupData()
        CountdownWidget().updateAll(applicationContext)
        if (GlanceAppWidgetManager(applicationContext).getGlanceIds(CountdownWidget::class.java).isNotEmpty()) {
            WidgetScheduler.schedule(applicationContext)
        }
        return Result.success()
    }

    private companion object {
        const val SESSION_WAIT_MILLIS = 30_000L
    }
}
