@file:UseSerializers(UuidSerializer::class, DecimalSerializer::class)

package ch.jonathanlemaine.giftmanager.core.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Relevé de prix d'un lien (#39), tel que renvoyé par `my_price_history` — iOS : `PriceCheck`.
 * Le prix et le stock sont `null` quand la boutique ne les a pas donnés.
 */
@Serializable
data class PriceCheck(
    @SerialName("link_id") val linkId: UUID,
    val url: String,
    val price: BigDecimal? = null,
    val currency: String? = null,
    @SerialName("in_stock") val inStock: Boolean? = null,
    @SerialName("checked_at") @Serializable(with = InstantSerializer::class) val checkedAt: Instant,
)
