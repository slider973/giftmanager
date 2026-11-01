package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.prices.PriceTrend
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * « Suivi du prix » (#77) : une ligne par lien suivi, sous les liens de la fiche. Absente sans relevé.
 * Câblage minimal : la mise en forme (courbe, badges de tendance) est à reprendre par l'ui-designer.
 */
@Composable
fun PriceHistorySection(trends: List<PriceTrend>, modifier: Modifier = Modifier) {
    if (trends.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        SectionHeader(title = stringResource(R.string.gift_detail_price_history))
        trends.forEach { trend ->
            val price = Money.format(trend.latest, trend.currency)
            Text("${trend.storeName}${price?.let { " · $it" }.orEmpty()}")
        }
    }
}
