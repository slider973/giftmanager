package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import java.math.BigDecimal
import java.util.UUID

/** Budgets privés (#40) : un budget n'est lu et modifié que par son auteur (RLS). */
interface BudgetRepository {
    /** Mes budgets avec ce qui est déjà engagé. Sans ordre garanti par le serveur : le tri est côté client. */
    suspend fun myBudgets(): List<Budget>

    /** Crée le budget de ce périmètre et de cette devise, ou met son montant à jour ; renvoie son identifiant. */
    suspend fun setBudget(childId: UUID?, eventId: UUID?, amount: BigDecimal, currency: String): UUID

    suspend fun deleteBudget(id: UUID)
}

class SupabaseBudgetRepository(private val client: SupabaseClient) : BudgetRepository {
    override suspend fun myBudgets(): List<Budget> = client.postgrest.rpc(Rpc.MY_BUDGETS).decodeTolerantList()

    override suspend fun setBudget(childId: UUID?, eventId: UUID?, amount: BigDecimal, currency: String): UUID =
        client.postgrest.rpc(Rpc.SET_BUDGET, ItemPayloads.setBudgetParams(childId, eventId, amount, currency)).rpcUuid()

    override suspend fun deleteBudget(id: UUID) {
        client.from("budgets").delete { filter { eq("id", id.toString()) } }
    }
}
