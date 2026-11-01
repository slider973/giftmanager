package ch.jonathanlemaine.giftmanager.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Jeton FCM de l'appareil : isolé pour tester [DeviceTokenRegistrar] sans Firebase. */
interface PushTokenSource {
    /** Jeton courant ; `null` si le push est désactivé ou si Firebase n'en fournit pas (hors ligne, service Google absent). */
    suspend fun currentToken(): String?

    /** Invalide le jeton côté Firebase (déconnexion) ; silencieux en cas d'échec. */
    suspend fun deleteToken()
}

/**
 * Firebase initialisé à la main depuis [FirebaseConfig] (pas de plugin google-services). Sans configuration valide,
 * [isAvailable] reste faux : aucun appel à Firebase n'est fait, l'app fonctionne sans push.
 */
// FirebaseMessaging.token et deleteToken sont signalés « Deprecated in Java » par le compilateur, sans remplaçant publié.
@Suppress("DEPRECATION")
class PushRuntime(private val context: Context, private val config: FirebaseConfig) : PushTokenSource {
    val isAvailable: Boolean by lazy { initialize() }

    private fun initialize(): Boolean {
        if (!config.isConfigured) return false
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(config.apiKey)
                    .setApplicationId(config.appId)
                    .setProjectId(config.projectId)
                    .setGcmSenderId(config.senderId)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            true
        }.getOrDefault(false)
    }

    override suspend fun currentToken(): String? {
        if (!isAvailable) return null
        return suspendCancellableCoroutine { continuation ->
            runCatching { FirebaseMessaging.getInstance().token }
                .onFailure { continuation.resume(null) }
                .onSuccess { task ->
                    task.addOnCompleteListener { if (continuation.isActive) continuation.resume(if (it.isSuccessful) it.result else null) }
                }
        }
    }

    override suspend fun deleteToken() {
        if (!isAvailable) return
        suspendCancellableCoroutine { continuation ->
            runCatching { FirebaseMessaging.getInstance().deleteToken() }
                .onFailure { continuation.resume(Unit) }
                .onSuccess { task -> task.addOnCompleteListener { if (continuation.isActive) continuation.resume(Unit) } }
        }
    }
}
