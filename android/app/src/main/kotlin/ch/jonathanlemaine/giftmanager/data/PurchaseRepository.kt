package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.MyContribution
import ch.jonathanlemaine.giftmanager.core.model.MyReservation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

/** Mes achats (#74) : cadeaux que j'ai réservés ou achetés, et mes parts de cagnotte. Lecture seule, propre à l'appelant. */
interface PurchaseRepository {
    suspend fun myReservations(): List<MyReservation>

    suspend fun myContributions(): List<MyContribution>
}

class SupabasePurchaseRepository(private val client: SupabaseClient) : PurchaseRepository {
    override suspend fun myReservations(): List<MyReservation> = client.postgrest.rpc(Rpc.MY_RESERVATIONS).decodeTolerantList()

    override suspend fun myContributions(): List<MyContribution> = client.postgrest.rpc(Rpc.MY_CONTRIBUTIONS).decodeTolerantList()
}
