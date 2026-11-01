package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.GiftManagerApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Réception des messages FCM (#76). Les messages sont « data-only » (voir `supabase/functions/_shared/fcm.ts`) :
 * `onMessageReceived` est donc appelé app au premier plan comme en arrière-plan, et construit la notification
 * ([NotificationPayloadParser] → [NotificationPresenter]).
 */
class GiftFirebaseMessagingService : FirebaseMessagingService() {
    private val container get() = (application as GiftManagerApp).container

    // Le compilateur signale ce point d'entrée comme obsolète sans en proposer d'autre : c'est bien celui de FCM.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        container.onPushTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = NotificationPayloadParser.parse(message.data) ?: return
        // Lecture locale et brève, sur le fil de travail de FCM.
        val familyNews = runBlocking { container.localPrefs.notifyFamilyNews.first() }
        if (!NotificationPayloadParser.shouldDisplay(payload, familyNews)) return
        container.notificationPresenter.show(payload)
    }
}
