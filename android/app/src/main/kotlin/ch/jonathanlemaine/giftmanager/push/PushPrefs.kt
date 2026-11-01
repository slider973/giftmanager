package ch.jonathanlemaine.giftmanager.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Jeton déjà enregistré côté serveur, pour [userId] : inutile de le renvoyer tant que ni l'un ni l'autre ne change. */
data class RegisteredToken(val userId: UUID, val token: String)

/** Mémoire de l'appareil pour les notifications push (#76). */
interface PushPrefs {
    suspend fun registeredToken(): RegisteredToken?

    suspend fun setRegisteredToken(value: RegisteredToken?)

    /** La boîte de dialogue de permission du système a déjà été affichée. */
    suspend fun permissionAsked(): Boolean

    suspend fun setPermissionAsked()

    /** La feuille d'explication a déjà été affichée. */
    suspend fun promptShown(): Boolean

    suspend fun setPromptShown()
}

class DataStorePushPrefs(private val dataStore: DataStore<Preferences>) : PushPrefs {
    override suspend fun registeredToken(): RegisteredToken? {
        val prefs = dataStore.data.first()
        val userId = prefs[USER]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val token = prefs[TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        return RegisteredToken(userId, token)
    }

    override suspend fun setRegisteredToken(value: RegisteredToken?) {
        dataStore.edit {
            if (value == null) {
                it.remove(USER)
                it.remove(TOKEN)
            } else {
                it[USER] = value.userId.toString()
                it[TOKEN] = value.token
            }
        }
    }

    override suspend fun permissionAsked(): Boolean = dataStore.data.first()[ASKED] ?: false

    override suspend fun setPermissionAsked() {
        dataStore.edit { it[ASKED] = true }
    }

    override suspend fun promptShown(): Boolean = dataStore.data.first()[PROMPTED] ?: false

    override suspend fun setPromptShown() {
        dataStore.edit { it[PROMPTED] = true }
    }

    companion object {
        val USER = stringPreferencesKey("push_registered_user")
        val TOKEN = stringPreferencesKey("push_registered_token")
        val ASKED = booleanPreferencesKey("push_permission_asked")
        val PROMPTED = booleanPreferencesKey("push_prompt_shown")
    }
}
