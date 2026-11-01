package ch.jonathanlemaine.giftmanager

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.data.FamilyRepository
import ch.jonathanlemaine.giftmanager.data.GiftRepository
import ch.jonathanlemaine.giftmanager.data.HouseholdRepository
import ch.jonathanlemaine.giftmanager.data.ProfileRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseFamilyRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseGiftRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseHouseholdRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseProfileRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseProvider
import ch.jonathanlemaine.giftmanager.data.auth.AuthRepository
import ch.jonathanlemaine.giftmanager.data.auth.CredentialManagerGoogleSource
import ch.jonathanlemaine.giftmanager.data.auth.GoogleCredentialSource
import ch.jonathanlemaine.giftmanager.data.auth.SupabaseAuthBackend
import ch.jonathanlemaine.giftmanager.data.auth.SupabaseAuthRepository
import ch.jonathanlemaine.giftmanager.data.prefs.CurrentGroupStore
import ch.jonathanlemaine.giftmanager.data.prefs.DataStoreCurrentGroupStore
import ch.jonathanlemaine.giftmanager.data.prefs.DataStoreLocalPrefs
import ch.jonathanlemaine.giftmanager.data.prefs.LocalPrefs
import ch.jonathanlemaine.giftmanager.data.prefs.giftDataStore
import ch.jonathanlemaine.giftmanager.ui.auth.SignInViewModel
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeViewModel
import ch.jonathanlemaine.giftmanager.ui.family.GroupSetupViewModel
import ch.jonathanlemaine.giftmanager.ui.main.MainViewModel
import ch.jonathanlemaine.giftmanager.ui.onboarding.ProfileSetupViewModel
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileViewModel
import ch.jonathanlemaine.giftmanager.ui.root.RootViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Injection de dépendances manuelle : un seul conteneur, créé au lancement de l'app. */
class AppContainer(private val config: AppConfig, private val context: Context) {
    private val supabase by lazy { SupabaseProvider.create(config) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: GiftRepository by lazy { SupabaseGiftRepository(supabase) }

    val googleCredentialSource: GoogleCredentialSource by lazy { CredentialManagerGoogleSource(config.googleWebClientId) }

    val authRepository: AuthRepository by lazy { SupabaseAuthRepository(SupabaseAuthBackend(supabase), config, googleCredentialSource) }

    val profileRepository: ProfileRepository by lazy { SupabaseProfileRepository(supabase) }
    val familyRepository: FamilyRepository by lazy { SupabaseFamilyRepository(supabase) }
    val householdRepository: HouseholdRepository by lazy { SupabaseHouseholdRepository(supabase) }

    val currentGroupStore: CurrentGroupStore by lazy { DataStoreCurrentGroupStore(giftDataStore(context)) }
    val localPrefs: LocalPrefs by lazy { DataStoreLocalPrefs(giftDataStore(context)) }

    /** État global de la session ; commence à suivre l'authentification dès sa première utilisation. */
    val session: AppSession by lazy {
        AppSession(authRepository, profileRepository, familyRepository, householdRepository, currentGroupStore, appScope)
            .also(AppSession::start)
    }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when {
                modelClass.isAssignableFrom(SignInViewModel::class.java) -> SignInViewModel(authRepository)
                modelClass.isAssignableFrom(RootViewModel::class.java) -> RootViewModel(session)
                modelClass.isAssignableFrom(ProfileSetupViewModel::class.java) -> ProfileSetupViewModel(session)
                modelClass.isAssignableFrom(GroupSetupViewModel::class.java) -> GroupSetupViewModel(session)
                modelClass.isAssignableFrom(FamilyHomeViewModel::class.java) -> FamilyHomeViewModel(session)
                modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(session, localPrefs)
                modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(session)
                else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
            } as T
    }
}
