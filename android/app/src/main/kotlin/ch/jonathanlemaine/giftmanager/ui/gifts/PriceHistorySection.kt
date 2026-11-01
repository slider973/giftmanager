package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.prices.PriceTrend
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * « Suivi du prix » (#77, iOS : `PriceHistorySection`) sous les liens de la fiche d'un cadeau que j'ai réservé.
 * Le serveur ne renvoie l'historique qu'à la personne qui a réservé : sans relevé, la section est absente.
 * Une carte par boutique, puis la légende « Relevé chaque jour… ».
 */
@Composable
fun PriceHistorySection(trends: List<PriceTrend>, modifier: Modifier = Modifier) {
    if (trends.isEmpty()) return
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        SectionHeader(title = stringResource(R.string.gift_detail_price_history))
        trends.forEach { trend -> PriceTrendRow(trend) }
        IconLabel(
            icon = GiftIcons.Bell,
            text = stringResource(R.string.price_trend_legend),
            style = type.caption,
            color = colors.textSecondary,
        )
    }
}

/**
 * Une boutique (`PriceTrendRow`) : nom, dernier prix, courbe à paliers (dès deux relevés), écart depuis le premier
 * relevé, pastille « Plus en stock ». Un seul nœud TalkBack : « Galaxus, 169,00 CHF, en baisse de 30,00 CHF depuis
 * le 26 oct., plus en stock » — la courbe, purement visuelle, n'y figure pas.
 */
@Composable
fun PriceTrendRow(trend: PriceTrend, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val shape = GiftTheme.radius.fieldShape
    val priceText = Money.format(trend.latest, trend.currency)
    val change = changeTexts(trend)
    val outOfStock = trend.inStock == false
    val outOfStockA11y = stringResource(R.string.price_trend_a11y_out_of_stock)
    val description = listOfNotNull(trend.storeName, priceText, change.spoken, outOfStockA11y.takeIf { outOfStock })
        .joinToString(", ")
    val stacked = LocalDensity.current.fontScale >= 1.5f

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.separator, shape)
            .padding(spacing.m)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(trend.storeName, style = type.callout, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (priceText != null) {
                Text(priceText, style = type.callout.semiBold().tabular(), color = colors.textPrimary)
            }
        }

        if (trend.points.size > 1) PriceStepChart(trend)

        val changeLabel = @Composable { rowModifier: Modifier ->
            IconLabel(
                icon = change.icon,
                text = change.text,
                style = if (trend.direction == PriceTrend.Direction.DOWN) type.captionBold.tabular() else type.caption.tabular(),
                color = if (trend.direction == PriceTrend.Direction.DOWN) colors.availableFg else colors.textSecondary,
                modifier = rowModifier,
            )
        }
        if (stacked) {
            changeLabel(Modifier)
            if (outOfStock) OutOfStockPill()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                changeLabel(Modifier.weight(1f))
                if (outOfStock) OutOfStockPill()
            }
        }
    }
}

private fun TextStyle.semiBold(): TextStyle = copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

/** Libellé de l'écart, affiché et lu (TalkBack dit « en baisse de » plutôt que « de moins »). */
private class ChangeTexts(val icon: ImageVector, val text: String, val spoken: String)

@Composable
private fun changeTexts(trend: PriceTrend): ChangeTexts {
    val amount = Money.format(trend.change.abs(), trend.currency).orEmpty()
    val since = trend.first?.date?.let(::dayText)
    return when (trend.direction) {
        PriceTrend.Direction.DOWN -> ChangeTexts(
            GiftIcons.TrendDown,
            since?.let { stringResource(R.string.price_trend_down_since, amount, it) } ?: stringResource(R.string.price_trend_down, amount),
            since?.let { stringResource(R.string.price_trend_a11y_down_since, amount, it) } ?: stringResource(R.string.price_trend_a11y_down, amount),
        )
        PriceTrend.Direction.UP -> ChangeTexts(
            GiftIcons.TrendUp,
            since?.let { stringResource(R.string.price_trend_up_since, amount, it) } ?: stringResource(R.string.price_trend_up, amount),
            since?.let { stringResource(R.string.price_trend_a11y_up_since, amount, it) } ?: stringResource(R.string.price_trend_a11y_up, amount),
        )
        PriceTrend.Direction.FLAT -> when {
            trend.points.isEmpty() -> ChangeTexts(
                GiftIcons.TrendFlat,
                stringResource(R.string.price_trend_unchecked),
                stringResource(R.string.price_trend_a11y_unchecked),
            )
            else -> ChangeTexts(
                GiftIcons.TrendFlat,
                since?.let { stringResource(R.string.price_trend_flat_since, it) } ?: stringResource(R.string.price_trend_flat),
                since?.let { stringResource(R.string.price_trend_a11y_flat_since, it) } ?: stringResource(R.string.price_trend_a11y_flat),
            )
        }
    }
}

private val frenchLocale: Locale = Locale.Builder().setLanguage("fr").setRegion("FR").build()

/** « 26 oct. » dans le fuseau du téléphone, insécable : le jour ne se sépare pas du mois en grande police. */
private fun dayText(date: Instant): String =
    DateTimeFormatter.ofPattern("d MMM", frenchLocale).withZone(ZoneId.systemDefault()).format(date).replace(' ', '\u00A0')

/**
 * Mini-courbe à paliers (`.interpolationMethod(.stepEnd)` d'iOS) : chaque prix tient jusqu'au relevé suivant. Ni axes
 * ni graduations ; le dernier relevé est marqué d'un point, vert si le prix a baissé. Masquée pour TalkBack (la carte
 * entière est lue en une phrase).
 */
@Composable
private fun PriceStepChart(trend: PriceTrend) {
    val colors = GiftTheme.colors
    val line = colors.primary
    val dot = if (trend.direction == PriceTrend.Direction.DOWN) colors.availableFg else colors.primary
    val points = trend.points
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val prices = points.map { it.price.toDouble() }
        val low = prices.min()
        val high = prices.max()
        // Même marge verticale qu'iOS : une variation minime ne remplit pas toute la hauteur.
        val pad = maxOf((high - low) * 0.2, high * 0.02, 1.0)
        val bottom = low - pad
        val range = (high + pad) - bottom
        val start = points.first().date.toEpochMilli().toDouble()
        val span = (points.last().date.toEpochMilli() - points.first().date.toEpochMilli()).toDouble()
        val inset = 4.dp.toPx()
        val width = size.width - inset * 2
        fun x(index: Int): Float {
            val fraction = if (span > 0) (points[index].date.toEpochMilli() - start) / span else index.toDouble() / (points.size - 1)
            return inset + (width * fraction).toFloat()
        }
        fun y(index: Int): Float = (size.height * (1 - (prices[index] - bottom) / range)).toFloat()

        val path = Path().apply {
            moveTo(x(0), y(0))
            for (i in 1 until points.size) {
                lineTo(x(i), y(i - 1))
                lineTo(x(i), y(i))
            }
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.lastIndex
        drawCircle(dot, radius = 3.5.dp.toPx(), center = Offset(x(last), y(last)))
    }
}

@Composable
private fun OutOfStockPill() {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    IconLabel(
        icon = GiftIcons.OutOfStock,
        text = stringResource(R.string.price_trend_out_of_stock),
        style = GiftTheme.type.captionBold,
        color = colors.takenFg,
        modifier = Modifier
            .background(colors.takenBg, CapsuleShape)
            .padding(horizontal = spacing.s, vertical = spacing.xs),
    )
}

/**
 * Icône + texte (`Label` d'iOS) : l'icône suit la taille du texte et reste centrée sur la première ligne quand le
 * texte passe à la ligne (grande police).
 */
@Composable
private fun IconLabel(icon: ImageVector, text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val firstLine = with(LocalDensity.current) { style.lineHeight.toDp() }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs + 2.dp),
        modifier = modifier,
    ) {
        Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(style.glyphSize()))
        }
        Text(text, style = style, color = color)
    }
}

@GiftPreviews
@Composable
private fun PriceHistorySectionPreview() = PreviewFrame {
    val now = Instant.parse("2026-11-01T09:00:00Z")
    fun daysAgo(days: Long) = now.minus(days, ChronoUnit.DAYS)
    fun trend(url: String, currency: String, inStock: Boolean?, vararg prices: Pair<Long, Int>) = PriceTrend(
        linkId = UUID.randomUUID(),
        url = url,
        points = prices.map { (days, price) -> PriceTrend.Point(daysAgo(days), BigDecimal(price)) },
        currency = currency,
        inStock = inStock,
        lastChecked = now,
    )
    PriceHistorySection(
        listOf(
            // Baisse
            trend("https://www.galaxus.ch/x", "CHF", true, 6L to 199, 3L to 189, 0L to 169),
            // Hausse
            trend("https://www.fnac.com/x", "EUR", true, 9L to 59, 4L to 59, 1L to 64),
            // Stable
            trend("https://www.manor.ch/x", "CHF", true, 12L to 45, 0L to 45),
            // Rupture, un seul relevé
            trend("https://www.amazon.fr/x", "EUR", false, 6L to 200),
            // Un seul relevé, en stock
            trend("https://www.decathlon.fr/x", "EUR", null, 2L to 35),
        ),
    )
}
