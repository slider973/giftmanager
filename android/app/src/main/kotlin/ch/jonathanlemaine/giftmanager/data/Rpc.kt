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

    const val P_NAME = "p_name"
    const val P_HOUSEHOLD_NAME = "p_household_name"
    const val P_COUNTRY = "p_country"
    const val P_CODE = "p_code"
    const val P_GROUP = "p_group"
}

/** Réponse d'une RPC qui renvoie un `uuid` : PostgREST le sérialise en chaîne JSON (`"3f2a…"`). */
fun parseRpcUuid(body: String): UUID = UUID.fromString(GiftJson.read.decodeFromString<String>(body))

internal fun PostgrestResult.rpcUuid(): UUID = parseRpcUuid(data)

/** Décode une réponse en liste avec la lecture tolérante de l'app (clés de jointure inconnues ignorées). */
internal inline fun <reified T> PostgrestResult.decodeTolerantList(): List<T> = GiftJson.read.decodeFromString(data)
