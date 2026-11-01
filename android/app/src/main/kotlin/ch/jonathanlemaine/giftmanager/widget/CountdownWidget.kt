package ch.jonathanlemaine.giftmanager.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ch.jonathanlemaine.giftmanager.MainActivity
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.widget.WidgetContent
import ch.jonathanlemaine.giftmanager.core.widget.WidgetEvent
import ch.jonathanlemaine.giftmanager.core.widget.WidgetModel
import ch.jonathanlemaine.giftmanager.data.widget.DataStoreWidgetSnapshotStore
import ch.jonathanlemaine.giftmanager.ui.theme.ThemeTokens
import java.time.LocalDate

/**
 * Widget d'écran d'accueil (#80) : prochain événement et compte à rebours, comme iOS mais sans les compteurs
 * « à acheter / achetés » (le widget est visible de tous, voir [ch.jonathanlemaine.giftmanager.core.widget.WidgetSnapshot]).
 */
class CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = DataStoreWidgetSnapshotStore(context).read()
        provideContent {
            // Les jours sont calculés ici, à chaque rendu.
            val model = WidgetContent.from(snapshot, LocalDate.now())
            Content(context, model)
        }
    }

    @Composable
    private fun Content(context: Context, model: WidgetModel) {
        Column(
            modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(BACKGROUND).padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (model) {
                WidgetModel.SignedOut -> Message(context.getString(R.string.widget_signed_out))
                WidgetModel.NoEvent -> Message(context.getString(R.string.widget_no_event))
                is WidgetModel.Ready -> Countdown(model)
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Text(text, style = TextStyle(color = TEXT_SECONDARY, fontSize = 14.sp))
    }

    @Composable
    private fun Countdown(model: WidgetModel.Ready) {
        val medium = LocalSize.current.width >= MEDIUM.width
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(emoji(model.next), style = TextStyle(fontSize = 22.sp))
                Text(
                    WidgetContent.countdownText(model.days),
                    style = TextStyle(color = PRIMARY, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    model.next.title,
                    style = TextStyle(color = TEXT_PRIMARY, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            if (medium && model.upcoming.isNotEmpty()) {
                Spacer(GlanceModifier.width(16.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    val today = LocalDate.now()
                    model.upcoming.forEach { event ->
                        Text(
                            "${emoji(event)} ${WidgetContent.countdownText(WidgetContent.daysUntil(event, today))} · ${event.title}",
                            style = TextStyle(color = TEXT_SECONDARY, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    private fun emoji(event: WidgetEvent): String =
        when (event.kind) {
            GiftEventKind.CHRISTMAS -> "🎄"
            GiftEventKind.BIRTHDAY -> "🎂"
            GiftEventKind.OTHER -> "🎁"
        }

    private companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)

        fun token(select: (ThemeTokens) -> Long) =
            ColorProvider(day = Color(select(ThemeTokens.Light)), night = Color(select(ThemeTokens.Dark)))

        val BACKGROUND = token { it.background }
        val PRIMARY = token { it.primary }
        val TEXT_PRIMARY = token { it.textPrimary }
        val TEXT_SECONDARY = token { it.textSecondary }
    }
}
