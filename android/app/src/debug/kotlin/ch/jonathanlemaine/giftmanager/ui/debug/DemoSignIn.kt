package ch.jonathanlemaine.giftmanager.ui.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import ch.jonathanlemaine.giftmanager.GiftManagerApp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Compte de démo du Supabase local (`supabase/seed.sql`) : `<nom>@demo.local`, mot de passe `demo1234`. */
internal data class DemoAccount(val name: String) {
    val email: String get() = "$name@demo.local"
    val password: String get() = DemoAccounts.PASSWORD
    val label: String get() = name.replaceFirstChar { it.titlecase(Locale.ROOT) }
}

internal object DemoAccounts {
    const val PASSWORD = "demo1234"
    val all: List<DemoAccount> = listOf("alice", "bob", "carol").map(::DemoAccount)
}

/** Connexion e-mail / mot de passe, réservée au debug : la session est publiée par `AuthRepository.state`. */
internal suspend fun SupabaseClient.signInDemo(account: DemoAccount) {
    auth.signInWith(Email) {
        email = account.email
        password = account.password
    }
}

/**
 * Menu « Compte de démo (dev) » (iOS : `DemoSignInButton`), discret sous le bouton Google : caption en
 * `textSecondary`, cible de 48 dp. [onError] reçoit le message de l'erreur Supabase (ou null).
 */
@Composable
internal fun DemoSignInMenu(enabled: Boolean, onError: (String?) -> Unit, modifier: Modifier = Modifier) {
    val client = (LocalContext.current.applicationContext as GiftManagerApp).container.supabaseClient
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button) { expanded = true }
            .heightIn(min = HitTarget.minimum)
            .padding(horizontal = GiftTheme.spacing.m),
    ) {
        Text(
            text = stringResource(R.string.demo_sign_in_title),
            style = GiftTheme.type.caption,
            color = GiftTheme.colors.textSecondary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DemoAccounts.all.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.label) },
                    onClick = {
                        expanded = false
                        scope.launch {
                            try {
                                client.signInDemo(account)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                onError(e.message)
                            }
                        }
                    },
                )
            }
        }
    }
}
