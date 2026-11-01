package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.PriceCheck
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID

/**
 * Suivi des prix des cadeaux réservés (#39, #77). L'historique n'est renvoyé qu'à la personne qui a réservé le
 * cadeau ; pour tout autre utilisateur, parents compris, la liste est vide.
 */
interface PriceHistoryRepository {
    /** Relevés des liens d'un cadeau, du plus récent au plus ancien. Vide si je ne l'ai pas réservé. */
    suspend fun priceHistory(itemId: UUID): List<PriceCheck>
}

class SupabasePriceHistoryRepository(private val client: SupabaseClient) : PriceHistoryRepository {
    override suspend fun priceHistory(itemId: UUID): List<PriceCheck> =
        client.postgrest.rpc(Rpc.MY_PRICE_HISTORY, ItemPayloads.itemParams(itemId)).decodeTolerantList()
}
