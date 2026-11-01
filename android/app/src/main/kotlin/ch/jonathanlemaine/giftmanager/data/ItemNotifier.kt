package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Notifications de la famille, déclenchées par la fonction Edge `notify-new-items`. Fire-and-forget : l'appelant
 * n'attend pas l'envoi et un échec ne doit jamais annuler l'action déjà réussie. Le serveur choisit seul les
 * destinataires (ni les parents pour une idée, ni l'acheteur pour une réservation).
 */
interface ItemNotifier {
    /** Prévient la famille des nouveaux cadeaux (les parents ne sont jamais notifiés des idées). */
    fun notifyNewItems(itemIds: List<UUID>)

    /** Alerte anonyme après une réservation (#58) : ni parents, ni acheteur, rien si l'audience est trop petite. */
    fun notifyReservation(itemId: UUID)

    /** Informe l'auteur d'une idée du verdict de son parent (#57). */
    fun notifyIdeaReview(itemId: UUID, decision: IdeaReview)

    /** Prévient les donateurs d'un cadeau qu'un merci les attend (#42, #75) ; le serveur choisit les destinataires. */
    fun notifyThanks(itemId: UUID)
}

/** Aucune notification (écrans et tests qui n'en veulent pas). */
object NoItemNotifier : ItemNotifier {
    override fun notifyNewItems(itemIds: List<UUID>) = Unit

    override fun notifyReservation(itemId: UUID) = Unit

    override fun notifyIdeaReview(itemId: UUID, decision: IdeaReview) = Unit

    override fun notifyThanks(itemId: UUID) = Unit
}

class SupabaseItemNotifier(private val client: SupabaseClient, private val scope: CoroutineScope) : ItemNotifier {
    override fun notifyNewItems(itemIds: List<UUID>) = send(newItemsBody(itemIds))

    override fun notifyReservation(itemId: UUID) = send(reservationBody(itemId))

    override fun notifyIdeaReview(itemId: UUID, decision: IdeaReview) = send(ideaReviewBody(itemId, decision))

    override fun notifyThanks(itemId: UUID) = send(thanksBody(itemId))

    private fun send(body: JsonObject) {
        scope.launch {
            try {
                client.functions.invoke(FUNCTION, body)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Confort : la réservation ou le cadeau est déjà enregistré.
            }
        }
    }

    internal companion object {
        const val FUNCTION = "notify-new-items"

        // Formats attendus par supabase/functions/notify-new-items/index.ts.
        fun newItemsBody(itemIds: List<UUID>): JsonObject = buildJsonObject {
            put("item_ids", JsonArray(itemIds.map { JsonPrimitive(it.toString()) }))
        }

        fun reservationBody(itemId: UUID): JsonObject = buildJsonObject {
            put("type", "reservation")
            put("item_id", itemId.toString())
        }

        fun thanksBody(itemId: UUID): JsonObject = buildJsonObject {
            put("type", "thanks")
            put("item_id", itemId.toString())
        }

        fun ideaReviewBody(itemId: UUID, decision: IdeaReview): JsonObject = buildJsonObject {
            put("type", "idea_review")
            put("item_id", itemId.toString())
            put("decision", ItemPayloads.wire(decision))
        }
    }
}
