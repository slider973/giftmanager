package ch.jonathanlemaine.giftmanager.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/** Obtient un jeton d'identité Google auprès de l'appareil. Remplaçable dans les tests. */
interface GoogleCredentialSource {
    /**
     * Affiche le sélecteur de compte Google et renvoie le jeton d'identité (JWT).
     * [nonceHash] est le SHA-256 du nonce brut, que Google inscrit dans le jeton.
     * [activityContext] n'est utilisé que le temps de l'appel : il n'est jamais conservé.
     *
     * @throws AuthError.Cancelled, AuthError.NoGoogleAccount, AuthError.GoogleUnavailable, AuthError.Failed
     */
    suspend fun requestIdToken(activityContext: Context, nonceHash: String): String
}

/** Implémentation Credential Manager avec le bouton explicite « Se connecter avec Google ». */
class CredentialManagerGoogleSource(private val webClientId: String) : GoogleCredentialSource {
    override suspend fun requestIdToken(activityContext: Context, nonceHash: String): String {
        val option = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
            .setNonce(nonceHash)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        try {
            val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
            return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            throw AuthError.Cancelled(e)
        } catch (e: NoCredentialException) {
            throw AuthError.NoGoogleAccount(e)
        } catch (e: GetCredentialProviderConfigurationException) {
            throw AuthError.GoogleUnavailable(e)
        } catch (e: GoogleIdTokenParsingException) {
            throw AuthError.Failed(e)
        } catch (e: GetCredentialException) {
            throw AuthError.Failed(e)
        } catch (e: IllegalArgumentException) {
            // createFrom refuse un type de credential inattendu.
            throw AuthError.Failed(e)
        }
    }
}
