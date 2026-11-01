package ch.jonathanlemaine.giftmanager.core.prices

import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.PriceCheck
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Évolution du prix d'un lien d'un cadeau réservé (#39), calculée à partir des relevés — iOS : `PriceTrend`. */
data class PriceTrend(
    val linkId: UUID,
    val url: String,
    /** Relevés avec un prix, du plus ancien au plus récent (même devise que le dernier relevé). */
    val points: List<Point>,
    val currency: String?,
    /** État de stock du dernier relevé qui le précise. */
    val inStock: Boolean?,
    val lastChecked: Instant,
) {
    data class Point(val date: Instant, val price: BigDecimal)

    enum class Direction { DOWN, UP, FLAT }

    val id: UUID get() = linkId
    val latest: BigDecimal? get() = points.lastOrNull()?.price
    val first: Point? get() = points.firstOrNull()

    /** Écart entre le premier et le dernier prix relevés. */
    val change: BigDecimal
        get() {
            val first = points.firstOrNull()?.price ?: return BigDecimal.ZERO
            val last = points.lastOrNull()?.price ?: return BigDecimal.ZERO
            return last - first
        }

    val direction: Direction
        get() = when {
            change.signum() < 0 -> Direction.DOWN
            change.signum() > 0 -> Direction.UP
            else -> Direction.FLAT
        }

    val storeName: String get() = StoreCatalog.store(url)?.name ?: "Boutique"

    private val rank: Int
        get() = when {
            inStock == false -> 0
            direction == Direction.DOWN -> 1
            else -> 2
        }

    companion object {
        /**
         * Regroupe les relevés par lien. Les liens sans aucun relevé exploitable sont ignorés ; les liens dont le prix
         * a baissé ou qui ne sont plus en stock passent en tête.
         */
        fun trends(checks: List<PriceCheck>): List<PriceTrend> {
            val trends = checks.groupBy { it.linkId }.mapNotNull { (linkId, rows) ->
                val sorted = rows.sortedBy { it.checkedAt }
                val last = sorted.lastOrNull() ?: return@mapNotNull null
                val currency = sorted.lastOrNull { it.price != null }?.currency
                val points = sorted.mapNotNull { row ->
                    val price = row.price ?: return@mapNotNull null
                    if (row.currency != currency) null else Point(row.checkedAt, price)
                }
                val inStock = sorted.lastOrNull { it.inStock != null }?.inStock
                if (points.isEmpty() && inStock == null) return@mapNotNull null
                PriceTrend(linkId, last.url, points, currency, inStock, last.checkedAt)
            }
            return trends.sortedWith(compareBy<PriceTrend> { it.rank }.thenByDescending { it.lastChecked })
        }
    }
}
