package ch.jonathanlemaine.giftmanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import ch.jonathanlemaine.giftmanager.data.GiftRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseGiftRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseProvider
import ch.jonathanlemaine.giftmanager.ui.home.HomeViewModel

/** Injection de dépendances manuelle : un seul conteneur, créé au lancement de l'app. */
class AppContainer(config: AppConfig) {
    private val supabase by lazy { SupabaseProvider.create(config) }

    val repository: GiftRepository by lazy { SupabaseGiftRepository(supabase) }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(repository)
                else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
            } as T
    }
}
