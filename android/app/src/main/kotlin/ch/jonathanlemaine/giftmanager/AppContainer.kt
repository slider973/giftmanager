package ch.jonathanlemaine.giftmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import ch.jonathanlemaine.giftmanager.data.GiftRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseGiftRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseProvider
import ch.jonathanlemaine.giftmanager.data.auth.AuthRepository
import ch.jonathanlemaine.giftmanager.data.auth.CredentialManagerGoogleSource
import ch.jonathanlemaine.giftmanager.data.auth.GoogleCredentialSource
import ch.jonathanlemaine.giftmanager.data.auth.SupabaseAuthBackend
import ch.jonathanlemaine.giftmanager.data.auth.SupabaseAuthRepository
import ch.jonathanlemaine.giftmanager.ui.auth.SignInViewModel
import ch.jonathanlemaine.giftmanager.ui.home.HomeViewModel
import ch.jonathanlemaine.giftmanager.ui.root.RootViewModel

/** Injection de dépendances manuelle : un seul conteneur, créé au lancement de l'app. */
class AppContainer(private val config: AppConfig) {
    private val supabase by lazy { SupabaseProvider.create(config) }

    val repository: GiftRepository by lazy { SupabaseGiftRepository(supabase) }

    val googleCredentialSource: GoogleCredentialSource by lazy { CredentialManagerGoogleSource(config.googleWebClientId) }

    val authRepository: AuthRepository by lazy { SupabaseAuthRepository(SupabaseAuthBackend(supabase), config, googleCredentialSource) }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository, authRepository)
                modelClass.isAssignableFrom(SignInViewModel::class.java) -> SignInViewModel(authRepository)
                modelClass.isAssignableFrom(RootViewModel::class.java) -> RootViewModel(authRepository)
                else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
            } as T
    }
}
