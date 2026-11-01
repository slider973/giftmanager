package ch.jonathanlemaine.giftmanager.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ch.jonathanlemaine.giftmanager.MainActivity
import ch.jonathanlemaine.giftmanager.R

/** Crée les canaux et affiche les notifications (les messages FCM sont « data-only » : Android ne les affiche pas seul). */
class NotificationPresenter(private val context: Context) {
    /** Idempotent : recréer un canal existant ne change rien (l'utilisateur garde ses réglages). */
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                channel(PushChannel.Purchases, R.string.channel_purchases_name, R.string.channel_purchases_description, NotificationManager.IMPORTANCE_HIGH),
                channel(PushChannel.Birthdays, R.string.channel_birthdays_name, R.string.channel_birthdays_description, NotificationManager.IMPORTANCE_DEFAULT),
                channel(PushChannel.Family, R.string.channel_family_name, R.string.channel_family_description, NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun channel(channel: PushChannel, name: Int, description: Int, importance: Int) =
        NotificationChannel(channel.id, context.getString(name), importance).apply { this.description = context.getString(description) }

    /** `false` si rien n'a été affiché (notifications désactivées ou non autorisées). */
    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS est vérifiée juste avant, voir canPost().
    fun show(payload: NotificationPayload): Boolean {
        if (!canPost()) return false
        ensureChannels()
        val id = payload.key.hashCode()
        val notification = NotificationCompat.Builder(context, payload.channel.id)
            .setSmallIcon(R.drawable.ic_stat_gift)
            .setContentTitle(payload.title.ifEmpty { context.getString(R.string.app_name) })
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(payload.target, requestCode = id))
            .build()
        // Même clé, même identifiant : une nouvelle notification du même sujet remplace la précédente.
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= NotificationPermission.RUNTIME_PERMISSION_SDK &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun contentIntent(target: NotificationTarget, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        target.toExtras().forEach { (key, value) -> intent.putExtra(key, value) }
        // Les extras ne comptent pas dans l'égalité des PendingIntent : le requestCode propre au sujet les distingue.
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
