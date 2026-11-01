package ch.jonathanlemaine.giftmanager.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Permission `POST_NOTIFICATIONS` lue sur l'appareil. La demande passe par le lanceur que `MainActivity` enregistre. */
class AndroidNotificationPermissionEnvironment(
    private val context: Context,
    private val activity: () -> Activity?,
) : NotificationPermissionEnvironment {
    /** Posé par `MainActivity` (un lanceur de résultat s'enregistre dans l'activité) ; `null` sans activité. */
    var launcher: (() -> Unit)? = null

    override val sdk: Int get() = Build.VERSION.SDK_INT

    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun shouldShowRationale(): Boolean =
        activity()?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: false

    override fun launchRequest() {
        launcher?.invoke()
    }
}

/** Réglages de notification de l'app dans le système : seule voie quand la permission est [NotificationPermission.PermanentlyDenied]. */
fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
