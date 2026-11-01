package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.PotParticipant
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.math.BigDecimal
import java.util.UUID

/** Cagnotte d'un cadeau (#35) : financer à plusieurs, chacun avec sa part. */
interface PotRepository {
    /** Participe (ou modifie sa part) ; la devise doit être celle de la cagnotte existante (`CURRENCY_MISMATCH`). */
    suspend fun join(itemId: UUID, amount: BigDecimal, currency: String)

    suspend fun leave(itemId: UUID)

    /** Participants et montants : renvoyé uniquement à un participant (vide sinon). */
    suspend fun participants(itemId: UUID): List<PotParticipant>
}

class SupabasePotRepository(private val client: SupabaseClient) : PotRepository {
    override suspend fun join(itemId: UUID, amount: BigDecimal, currency: String) {
        client.postgrest.rpc(Rpc.JOIN_POT, ItemPayloads.joinPotParams(itemId, amount, currency))
    }

    override suspend fun leave(itemId: UUID) {
        client.postgrest.rpc(Rpc.LEAVE_POT, ItemPayloads.itemParams(itemId))
    }

    override suspend fun participants(itemId: UUID): List<PotParticipant> =
        client.postgrest.rpc(Rpc.POT_PARTICIPANTS, ItemPayloads.itemParams(itemId)).decodeTolerantList()
}
