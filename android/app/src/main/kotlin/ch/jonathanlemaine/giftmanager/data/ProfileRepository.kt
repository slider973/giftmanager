package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import ch.jonathanlemaine.giftmanager.core.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/** Profil de l'utilisateur et ses préférences serveur (miroir de la partie « profil » de `GiftRepository.swift`). */
interface ProfileRepository {
    suspend fun myProfile(userId: UUID): Profile

    /** Met à jour l'identité ; les préférences de rappel ne sont pas touchées. */
    suspend fun updateProfile(userId: UUID, displayName: String, country: String, currency: String, onboarded: Boolean)

    suspend fun setBirthdayReminders(userId: UUID, enabled: Boolean)

    suspend fun setPurchaseReminders(userId: UUID, enabled: Boolean)

    /** Liste vide sans requête quand [ids] est vide. */
    suspend fun profiles(ids: List<UUID>): List<Profile>

    suspend fun deleteAccount()
}

@Serializable
internal data class ProfilePatch(
    @SerialName("display_name") val displayName: String,
    val country: String,
    val currency: String,
    val onboarded: Boolean,
)

@Serializable
internal data class BirthdayRemindersPatch(@SerialName("notify_birthday_reminders") val enabled: Boolean)

@Serializable
internal data class PurchaseRemindersPatch(@SerialName("notify_purchase_reminders") val enabled: Boolean)

class SupabaseProfileRepository(private val client: SupabaseClient) : ProfileRepository {
    override suspend fun myProfile(userId: UUID): Profile =
        GiftJson.read.decodeFromString<List<Profile>>(
            client.from("profiles").select { filter { eq("id", userId.toString()) } }.data,
        ).firstOrNull() ?: error("Profil introuvable")

    override suspend fun updateProfile(userId: UUID, displayName: String, country: String, currency: String, onboarded: Boolean) {
        client.from("profiles").update(ProfilePatch(displayName, country, currency, onboarded)) {
            filter { eq("id", userId.toString()) }
        }
    }

    override suspend fun setBirthdayReminders(userId: UUID, enabled: Boolean) {
        client.from("profiles").update(BirthdayRemindersPatch(enabled)) { filter { eq("id", userId.toString()) } }
    }

    override suspend fun setPurchaseReminders(userId: UUID, enabled: Boolean) {
        client.from("profiles").update(PurchaseRemindersPatch(enabled)) { filter { eq("id", userId.toString()) } }
    }

    override suspend fun profiles(ids: List<UUID>): List<Profile> {
        if (ids.isEmpty()) return emptyList()
        return client.from("profiles").select(Columns.ALL) {
            filter { isIn("id", ids.map(UUID::toString)) }
        }.decodeTolerantList()
    }

    override suspend fun deleteAccount() {
        client.postgrest.rpc(Rpc.DELETE_ACCOUNT)
    }
}
