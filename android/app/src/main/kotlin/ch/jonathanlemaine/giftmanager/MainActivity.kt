package ch.jonathanlemaine.giftmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.jonathanlemaine.giftmanager.ui.navigation.AppNavHost
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Bord à bord (imposé par Android 15+) : les écrans posent eux-mêmes les marges des barres système.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as GiftManagerApp).container
        setContent {
            GiftManagerTheme {
                AppNavHost(viewModelFactory = container.viewModelFactory)
            }
        }
    }
}
