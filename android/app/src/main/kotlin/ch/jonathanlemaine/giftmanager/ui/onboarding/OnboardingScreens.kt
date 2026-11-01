package ch.jonathanlemaine.giftmanager.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.auth.OnboardingPage
import ch.jonathanlemaine.giftmanager.ui.auth.SignInScreen
import ch.jonathanlemaine.giftmanager.ui.auth.SignInUiState
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ProfileFields
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Onboarding en quatre pages ([OnboardingPages]) : les trois premières avec « Continuer », la dernière est
 * l'écran de connexion Google. Câblage minimal : le `ui-designer` y ajoute le carrousel glissant et les points.
 */
@Composable
fun OnboardingScreen(
    signInState: SignInUiState,
    onSignIn: () -> Unit,
    onDismissSignInError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    if (OnboardingPages.isLast(page)) {
        SignInScreen(state = signInState, onSignIn = onSignIn, onDismiss = onDismissSignInError, modifier = modifier)
        return
    }
    val content = OnboardingPages.all[page]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.l),
        modifier = modifier.fcScreenBackground().fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            OnboardingPage(illustration = content.illustration, title = content.title, message = content.message)
        }
        PrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = { page = OnboardingPages.next(page) },
            trailingIcon = GiftIcons.ChevronRight,
            modifier = Modifier.padding(horizontal = GiftTheme.spacing.xl, vertical = GiftTheme.spacing.l),
        )
    }
}

/** Premier lancement : prénom, pays et devise. */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    onNameChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenColumn(modifier) {
        ScreenTitle(stringResource(R.string.profile_setup_title))
        BodyText(stringResource(R.string.profile_setup_message))
        ProfileFields(state.name, state.country, state.currency, onNameChange, onCountryChange, onCurrencyChange)
        PrimaryButton(
            text = stringResource(R.string.profile_setup_go),
            onClick = onSave,
            isLoading = state.isSaving,
            enabled = state.canSave,
        )
    }
    GiftErrorDialog(state.error, onDismissError)
}
