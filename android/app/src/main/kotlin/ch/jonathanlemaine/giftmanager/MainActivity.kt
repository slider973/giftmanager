package ch.jonathanlemaine.giftmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.jonathanlemaine.giftmanager.core.session.ShareIntent
import ch.jonathanlemaine.giftmanager.ui.navigation.AppNavHost
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import kotlinx.coroutines.launch

// FragmentActivity (et non ComponentActivity) : BiometricPrompt, à la sortie du mode enfant, l'exige.
class MainActivity : FragmentActivity() {
    private val container get() = (application as GiftManagerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bord à bord (imposé par Android 15+) : les écrans posent eux-mêmes les marges des barres système.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        container.attachActivity(this)
        // Après une recréation (rotation), l'intent d'origine est déjà traité.
        if (savedInstanceState == null) handleIntent(intent)
        // Taux de change indicatifs : lus du cache puis rafraîchis (au plus toutes les 6 h) à chaque retour au premier plan.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { container.currencyService.refreshIfNeeded() }
        }
        setContent {
            GiftManagerTheme {
                AppNavHost(viewModelFactory = container.viewModelFactory)
            }
        }
    }

    override fun onDestroy() {
        container.detachActivity(this)
        super.onDestroy()
    }

    // launchMode singleTop : un lien d'invitation ouvert avec l'app déjà lancée arrive ici.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // Relance depuis les tâches récentes : l'intent d'origine (invitation, partage) a déjà été traité.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        container.session.handleDeepLink(intent.dataString)
        handleShare(intent)
    }

    // Partage depuis une autre app : le lien ouvre l'ajout de cadeau ; un texte sans lien est signalé (jamais loggué).
    private fun handleShare(intent: Intent) {
        if (!ShareIntent.isTextShare(intent.action, intent.type)) return
        val link = ShareIntent.extractLink(
            intent.action,
            intent.type,
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
        )
        if (link != null) container.session.handleSharedLink(link) else container.session.handleShareWithoutLink()
    }
}
