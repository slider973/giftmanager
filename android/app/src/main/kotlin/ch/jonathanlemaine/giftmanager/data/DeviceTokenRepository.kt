package ch.jonathanlemaine.giftmanager.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Jeton FCM de l'appareil côté serveur (#76) : un jeton appartient à un seul compte, la RPC le réattribue au besoin. */
interface DeviceTokenBackend {
    suspend fun register(token: String)

    /** Supprime le jeton du compte connecté (RLS : « suppression des siens »). */
    suspend fun unregister(token: String)
}

class SupabaseDeviceTokenBackend(private val client: SupabaseClient) : DeviceTokenBackend {
    override suspend fun register(token: String) {
        client.postgrest.rpc(Rpc.REGISTER_DEVICE, registerParams(token))
    }

    override suspend fun unregister(token: String) {
        client.from(Rpc.DEVICE_TOKENS_TABLE).delete { filter { eq("token", token) } }
    }

    internal companion object {
        /** FCM n'a pas d'environnement « sandbox » : toujours `production`. */
        fun registerParams(token: String): JsonObject = buildJsonObject {
            put(Rpc.P_TOKEN, token)
            put(Rpc.P_ENVIRONMENT, Rpc.ENVIRONMENT_PRODUCTION)
            put(Rpc.P_PLATFORM, Rpc.PLATFORM_ANDROID)
        }
    }
}
