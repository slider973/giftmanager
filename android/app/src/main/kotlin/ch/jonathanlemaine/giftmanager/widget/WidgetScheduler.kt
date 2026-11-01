package ch.jonathanlemaine.giftmanager.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Planifie le prochain rafraîchissement du widget (#80). */
object WidgetScheduler {
    private const val WORK_NAME = "widget_refresh"
    private val PERIOD = Duration.ofHours(3)
    private val MIDNIGHT_MARGIN = Duration.ofMinutes(5)

    /** Politique iOS : toutes les 3 h, et peu après minuit (le compte à rebours change de jour). */
    fun delayUntilNext(clock: Clock): Duration {
        val now = ZonedDateTime.now(clock)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val afterMidnight = Duration.between(now, midnight).plus(MIDNIGHT_MARGIN)
        return minOf(PERIOD, afterMidnight)
    }

    fun schedule(context: Context, delay: Duration = delayUntilNext(Clock.systemDefaultZone())) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
