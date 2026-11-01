package ch.jonathanlemaine.giftmanager.ui.childmode

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.childmode.ParentAuthenticator
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Vérification de l'adulte via `BiometricPrompt` (iOS : `LAContext.deviceOwnerAuthentication`) : empreinte ou visage,
 * à défaut le code de l'appareil. [activity] donne l'activité au premier plan (`null` : aucune vérification possible).
 */
class BiometricParentAuthenticator(private val activity: () -> FragmentActivity?) : ParentAuthenticator {
    override fun isAvailable(): Boolean {
        val host = activity() ?: return false
        return BiometricManager.from(host).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(): Boolean {
        val host = activity() ?: return false
        when (BiometricManager.from(host).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            // Ni code ni biométrie sur l'appareil : l'appui long reste la seule protection (même choix qu'iOS).
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> return true
            else -> return false
        }
        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    // Annulation, trop d'essais, verrouillage : on reste en mode enfant. Un échec d'une empreinte
                    // (onAuthenticationFailed) laisse la boîte ouverte pour un nouvel essai.
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(host.getString(R.string.child_mode_auth_title))
                .setSubtitle(host.getString(R.string.child_mode_auth_subtitle))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
