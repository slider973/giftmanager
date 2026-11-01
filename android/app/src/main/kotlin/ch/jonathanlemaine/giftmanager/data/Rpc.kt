package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import io.github.jan.supabase.postgrest.result.PostgrestResult
import java.util.UUID
import kotlinx.serialization.decodeFromString

/**
 * Noms des fonctions Postgres (RPC) et de leurs paramètres, tels que définis dans `supabase/migrations`
 * (source de vérité). Centralisés pour être testés et ne pas dériver du schéma.
 */
object Rpc {
    const val CREATE_GROUP = "create_group"
    const val JOIN_GROUP = "join_group"
    const val CREATE_HOUSEHOLD = "create_household"
    const val JOIN_HOUSEHOLD = "join_household"
    const val SHARE_HOUSEHOLD = "share_household_with_group"
    const val UNSHARE_HOUSEHOLD = "unshare_household_from_group"
    const val DELETE_ACCOUNT = "delete_account"
    const val CHILDREN_RESERVATION_COUNTS = "children_reservation_counts"
    const val ENSURE_BIRTHDAY_EVENTS = "ensure_birthday_events"

    // Cadeaux, réservations, cagnottes et validation des idées (#73).
    const val CHILD_ITEMS = "child_items"
    const val RESERVE_ITEM = "reserve_item"
    const val CANCEL_RESERVATION = "cancel_reservation"
    const val SET_RESERVATION_PURCHASED = "set_reservation_purchased"
    const val JOIN_POT = "join_pot"
    const val LEAVE_POT = "leave_pot"
    const val POT_PARTICIPANTS = "pot_participants"
    const val REVIEW_IDEA = "review_idea"
    const val PENDING_IDEAS = "pending_ideas"
    const val IDEA_AUDIENCE = "idea_audience"

    // Mes achats et budgets (#74).
    const val MY_RESERVATIONS = "my_reservations"
    const val MY_CONTRIBUTIONS = "my_contributions"
    const val MY_BUDGETS = "my_budgets"
    const val SET_BUDGET = "set_budget"

    // Remerciements (#75).
    const val SEND_THANKS = "send_thanks"
    const val MY_THANKS = "my_thanks"
    const val REVEAL_MYSELF = "reveal_myself"
    const val ITEM_DONORS = "item_donors"

    // Suivi des prix (#77).
    const val MY_PRICE_HISTORY = "my_price_history"

    const val P_NAME = "p_name"
    const val P_HOUSEHOLD_NAME = "p_household_name"
    const val P_COUNTRY = "p_country"
    const val P_CODE = "p_code"
    const val P_GROUP = "p_group"
    const val P_EVENT = "p_event"
    const val P_CHILD = "p_child"
    const val P_KIND = "p_kind"
    const val P_ITEM = "p_item"
    const val P_PURCHASED = "p_purchased"
    const val P_AMOUNT = "p_amount"
    const val P_CURRENCY = "p_currency"
    const val P_DECISION = "p_decision"
    const val P_NOTE = "p_note"
    const val P_MESSAGE = "p_message"
    const val P_PHOTO_URL = "p_photo_url"
    const val P_REVEAL = "p_reveal"
}

/** Réponse d'une RPC qui renvoie un `uuid` : PostgREST le sérialise en chaîne JSON (`"3f2a…"`). */
fun parseRpcUuid(body: String): UUID = UUID.fromString(GiftJson.read.decodeFromString<String>(body))

/** Réponse d'une RPC qui renvoie un `integer` (ex. `idea_audience`) : un nombre JSON nu. */
fun parseRpcInt(body: String): Int = GiftJson.read.decodeFromString<Int>(body.trim())

internal fun PostgrestResult.rpcUuid(): UUID = parseRpcUuid(data)

internal fun PostgrestResult.rpcInt(): Int = parseRpcInt(data)

/** Décode une réponse en liste avec la lecture tolérante de l'app (clés de jointure inconnues ignorées). */
internal inline fun <reified T> PostgrestResult.decodeTolerantList(): List<T> = GiftJson.read.decodeFromString(data)
