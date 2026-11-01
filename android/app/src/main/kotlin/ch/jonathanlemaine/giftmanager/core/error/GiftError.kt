package ch.jonathanlemaine.giftmanager.core.error

import androidx.annotation.StringRes
import ch.jonathanlemaine.giftmanager.R
import io.github.jan.supabase.exceptions.HttpRequestException
import java.io.IOException

/**
 * Erreur présentable à l'utilisateur (miroir de `GiftError` iOS). Les erreurs métier du serveur sont des
 * codes dans le message de l'exception Postgres (`raise exception 'INVALID_INVITE_CODE'`) ; le message
 * brut ne doit jamais atteindre l'écran.
 *
 * Les textes vivent dans `strings.xml` ([messageRes]) : la logique ne porte aucune phrase.
 */
enum class GiftError(@StringRes val messageRes: Int) {
    Unavailable(R.string.error_item_unavailable),
    Owned(R.string.error_item_owned),
    NotFound(R.string.error_item_not_found),
    InvalidInvite(R.string.error_invalid_invite),
    AlreadyInHousehold(R.string.error_already_in_household),
    CurrencyMismatch(R.string.error_currency_mismatch),
    NotAMember(R.string.error_not_a_member),
    NoHousehold(R.string.error_no_household),
    NotAuthenticated(R.string.error_not_authenticated),
    Network(R.string.error_network),

    /** L'app parle à un schéma plus récent qu'elle : seule une mise à jour peut résoudre ça. */
    OutdatedApp(R.string.error_outdated_app),
    Other(R.string.error_generic),
    ;

    companion object {
        fun from(error: Throwable): GiftError {
            val text = generateSequence(error) { it.cause }.joinToString(" ") { "$it" }
            // Codes serveur, du plus précis au plus général (ITEM_NOT_FOUND avant la détection d'obsolescence).
            return when {
                "ITEM_UNAVAILABLE" in text -> Unavailable
                "ITEM_OWNED" in text -> Owned
                "ITEM_NOT_FOUND" in text || "RESERVATION_NOT_FOUND" in text -> NotFound
                "INVALID_INVITE_CODE" in text -> InvalidInvite
                "ALREADY_IN_HOUSEHOLD" in text -> AlreadyInHousehold
                "CURRENCY_MISMATCH" in text -> CurrencyMismatch
                "NOT_A_MEMBER" in text -> NotAMember
                "NO_HOUSEHOLD" in text -> NoHousehold
                "NOT_AUTHENTICATED" in text -> NotAuthenticated
                generateSequence(error) { it.cause }.any { it is IOException || it is HttpRequestException } -> Network
                // 42703 : colonne absente, 42883 : fonction absente, PGRST202 : RPC introuvable.
                "42703" in text || "42883" in text || "PGRST202" in text || "does not exist" in text -> OutdatedApp
                else -> Other
            }
        }
    }
}
