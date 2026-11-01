package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first

/** Accès aux données Supabase. L'interface est minimale pour l'instant : elle grossira avec les écrans. */
interface GiftRepository {
    /** Profil de l'utilisateur connecté, ou `null` sans session. */
    suspend fun currentProfile(): Profile?

    suspend fun signOut()
}

class SupabaseGiftRepository(private val client: SupabaseClient) : GiftRepository {
    override suspend fun currentProfile(): Profile? {
        // Attend la restauration de la session stockée avant de conclure qu'il n'y en a pas.
        client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        val user = client.auth.currentUserOrNull() ?: return null
        return client.from("profiles")
            .select { filter { eq("id", user.id) } }
            .decodeSingleOrNull<Profile>()
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }
}
