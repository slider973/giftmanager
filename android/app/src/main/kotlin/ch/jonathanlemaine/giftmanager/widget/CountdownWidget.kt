package ch.jonathanlemaine.giftmanager.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import ch.jonathanlemaine.giftmanager.MainActivity
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.widget.WidgetContent
import ch.jonathanlemaine.giftmanager.core.widget.WidgetEvent
import ch.jonathanlemaine.giftmanager.core.widget.WidgetModel
import ch.jonathanlemaine.giftmanager.data.widget.DataStoreWidgetSnapshotStore
import ch.jonathanlemaine.giftmanager.ui.theme.ThemeTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Widget d'écran d'accueil (#80) : prochain événement et compte à rebours, comme iOS mais sans les compteurs
 * « à acheter / achetés » (le widget est visible de tous, voir [ch.jonathanlemaine.giftmanager.core.widget.WidgetSnapshot]).
 *
 * Mise en page (iOS `CountdownWidgetView`) : vignette du type d'événement (`EventKindTile`) et date, puis le
 * « J-n » dominant et le titre, calés en bas. En moyen format, les événements suivants à droite d'un filet.
 * Couleurs : tokens via `ColorProvider(day, night)` ; coins arrondis par `cornerRadius` dès Android 12, par des
 * formes XML avant (`widget_background`, `widget_tile_*`). TalkBack lit le widget d'un bloc ([spokenReady]).
 */
class CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = DataStoreWidgetSnapshotStore(context).read()
        provideContent {
            // Les jours sont calculés ici, à chaque rendu.
            val today = LocalDate.now()
            Content(context, WidgetContent.from(snapshot, today), today)
        }
    }

    @Composable
    private fun Content(context: Context, model: WidgetModel, today: LocalDate) {
        val medium = LocalSize.current.width >= MEDIUM.width
        val description = when (model) {
            WidgetModel.SignedOut -> context.getString(R.string.widget_signed_out)
            WidgetModel.NoEvent -> context.getString(R.string.widget_no_event)
            is WidgetModel.Ready -> spokenReady(context, model, today, withUpcoming = medium)
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().appWidgetBackground().widgetBackground().padding(PADDING)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .semantics { contentDescription = description },
        ) {
            when (model) {
                WidgetModel.SignedOut -> Message(context.getString(R.string.widget_signed_out), R.drawable.gift_red, medium)
                WidgetModel.NoEvent -> Message(context.getString(R.string.widget_no_event), R.drawable.mascot_sleeping, medium)
                is WidgetModel.Ready -> Countdown(model, today, medium)
            }
        }
    }

    /** État vide (iOS `message(_:image:)`) : illustration décorative — le texte porte l'information. */
    @Composable
    private fun Message(text: String, @DrawableRes image: Int, medium: Boolean) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(image),
                contentDescription = null,
                modifier = GlanceModifier.size(if (medium) 44.dp else 36.dp),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text,
                style = TextStyle(
                    color = TEXT_SECONDARY,
                    fontSize = if (medium) 13.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 3,
            )
        }
    }

    @Composable
    private fun Countdown(model: WidgetModel.Ready, today: LocalDate, medium: Boolean) {
        val showUpcoming = medium && model.upcoming.isNotEmpty()
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Headline(
                event = model.next,
                days = model.days,
                medium = medium,
                // Toute la largeur du moyen format : on peut écrire la date en entier.
                longDate = medium && !showUpcoming,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            if (showUpcoming) {
                Spacer(GlanceModifier.width(12.dp))
                Box(GlanceModifier.width(1.dp).fillMaxHeight().background(SEPARATOR)) {}
                Spacer(GlanceModifier.width(12.dp))
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    model.upcoming.take(MAX_UPCOMING).forEachIndexed { index, event ->
                        if (index > 0) Spacer(GlanceModifier.height(10.dp))
                        UpcomingRow(event, WidgetContent.daysUntil(event, today))
                    }
                }
            }
        }
    }

    /** Prochain événement : vignette + date en haut, « J-n » dominant et titre calés en bas. */
    @Composable
    private fun Headline(event: WidgetEvent, days: Int, medium: Boolean, longDate: Boolean, modifier: GlanceModifier) {
        // « Aujourd'hui ! » et « Demain » sont des mots : plus petits, sur deux lignes au besoin, pour tenir en 110 dp.
        val word = days <= 1
        Column(modifier = modifier) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KindTile(event.kind, TILE)
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    if (longDate) Formatting.dateText(event.date) else shortDate(event),
                    style = TextStyle(color = TEXT_SECONDARY, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                WidgetContent.countdownText(days),
                style = TextStyle(color = PRIMARY, fontSize = countdownSize(word, medium), fontWeight = FontWeight.Bold),
                maxLines = if (word) 2 else 1,
            )
            Text(
                event.title,
                style = TextStyle(color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }

    /** Événement suivant (moyen format) : petite vignette, titre, « J-n · date ». */
    @Composable
    private fun UpcomingRow(event: WidgetEvent, days: Int) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KindTile(event.kind, TILE_SMALL)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    event.title,
                    style = TextStyle(color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    "${WidgetContent.countdownText(days)} · ${shortDate(event)}",
                    style = TextStyle(color = TEXT_SECONDARY, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }

    /** Équivalent Glance d'`EventKindTile` : 🎄 sur menthe, 🎂 sur rose, cadeau rouge sur pêche. */
    @Composable
    private fun KindTile(kind: GiftEventKind, size: Dp) {
        val context = LocalContext.current
        Box(
            modifier = GlanceModifier.size(size).roundedBackground(kind.tileColor, size * TILE_RADIUS, kind.tileFallback),
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                GiftEventKind.OTHER -> Image(
                    provider = ImageProvider(R.drawable.gift_red),
                    contentDescription = context.getString(R.string.event_kind_other),
                    modifier = GlanceModifier.size(size * GIFT_SCALE),
                )
                else -> Text(
                    if (kind == GiftEventKind.CHRISTMAS) "🎄" else "🎂",
                    // Émoji à la moitié de la vignette, qui est en dp : on annule l'échelle de police pour qu'il y tienne.
                    style = TextStyle(fontSize = (size.value * EMOJI_SCALE / context.resources.configuration.fontScale).sp),
                    maxLines = 1,
                )
            }
        }
    }

    private fun countdownSize(word: Boolean, medium: Boolean): TextUnit =
        when {
            word -> if (medium) 22.sp else 20.sp
            else -> if (medium) 30.sp else 28.sp
        }

    private fun shortDate(event: WidgetEvent): String = SHORT_DATE.format(event.date.date).replace(' ', ' ')

    /** « Noël, dans 24 jours, le 25 décembre 2026. Ensuite : … » */
    private fun spokenReady(context: Context, model: WidgetModel.Ready, today: LocalDate, withUpcoming: Boolean): String {
        val next = spokenEvent(context, model.next, model.days)
        if (!withUpcoming || model.upcoming.isEmpty()) return next
        val upcoming = model.upcoming.take(MAX_UPCOMING)
            .joinToString(" ; ") { spokenEvent(context, it, WidgetContent.daysUntil(it, today)) }
        return "$next. ${context.getString(R.string.widget_a11y_upcoming, upcoming)}"
    }

    private fun spokenEvent(context: Context, event: WidgetEvent, days: Int): String {
        val whenText = when {
            days < 1 -> context.getString(R.string.widget_a11y_today)
            days == 1 -> context.getString(R.string.widget_a11y_tomorrow)
            else -> context.resources.getQuantityString(R.plurals.widget_a11y_in_days, days, days)
        }
        return context.getString(R.string.widget_a11y_event, event.title, whenText, Formatting.dateText(event.date))
    }

    private val GiftEventKind.tileColor: GlanceColor
        get() = when (this) {
            GiftEventKind.CHRISTMAS -> TILE_MINT
            GiftEventKind.BIRTHDAY -> TILE_PINK
            GiftEventKind.OTHER -> TILE_PEACH
        }

    private val GiftEventKind.tileFallback: Int
        get() = when (this) {
            GiftEventKind.CHRISTMAS -> R.drawable.widget_tile_mint
            GiftEventKind.BIRTHDAY -> R.drawable.widget_tile_pink
            GiftEventKind.OTHER -> R.drawable.widget_tile_peach
        }

    private companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)

        /** `Spacing.m` : tient dans 110 dp de haut avec vignette, compte à rebours et titre. */
        val PADDING = 12.dp
        val TILE = 28.dp
        val TILE_SMALL = 24.dp

        /** Proportions d'`EventKindTile` : rayon `thumb` 14 sur 56, cadeau inscrit à 18 % du bord. */
        const val TILE_RADIUS = 0.25f
        const val GIFT_SCALE = 0.64f
        const val EMOJI_SCALE = 0.5f

        /** L'instantané garde 3 événements : le prochain et 2 suivants. */
        const val MAX_UPCOMING = 2

        val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

        fun token(select: (ThemeTokens) -> Long) =
            ColorProvider(day = Color(select(ThemeTokens.Light)), night = Color(select(ThemeTokens.Dark)))

        val BACKGROUND = token { it.background }
        val SEPARATOR = token { it.separator }
        val PRIMARY = token { it.primary }
        val TEXT_PRIMARY = token { it.textPrimary }
        val TEXT_SECONDARY = token { it.textSecondary }
        val TILE_MINT = token { it.pastelMint }
        val TILE_PINK = token { it.pastelPink }
        val TILE_PEACH = token { it.pastelPeach }

        /** Fond du widget, arrondi au rayon du lanceur dès Android 12 ; forme XML (rayon `card`) avant. */
        fun GlanceModifier.widgetBackground(): GlanceModifier =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                background(BACKGROUND).cornerRadius(android.R.dimen.system_app_widget_background_radius)
            } else {
                background(ImageProvider(R.drawable.widget_background))
            }

        /** `cornerRadius` n'existe qu'à partir d'Android 12 : avant, une forme XML aux mêmes couleurs. */
        fun GlanceModifier.roundedBackground(color: GlanceColor, radius: Dp, @DrawableRes fallback: Int): GlanceModifier =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                background(color).cornerRadius(radius)
            } else {
                background(ImageProvider(fallback))
            }
    }
}
