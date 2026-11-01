package ch.jonathanlemaine.giftmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.jonathanlemaine.giftmanager.ui.navigation.AppNavHost
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as GiftManagerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bord à bord (imposé par Android 15+) : les écrans posent eux-mêmes les marges des barres système.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Après une recréation (rotation), l'intent d'origine est déjà traité.
        if (savedInstanceState == null) handleInvite(intent)
        setContent {
            GiftManagerTheme {
                AppNavHost(viewModelFactory = container.viewModelFactory)
            }
        }
    }

    // launchMode singleTop : un lien d'invitation ouvert avec l'app déjà lancée arrive ici.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInvite(intent)
    }

    private fun handleInvite(intent: Intent?) = container.session.handleDeepLink(intent?.dataString)
}
