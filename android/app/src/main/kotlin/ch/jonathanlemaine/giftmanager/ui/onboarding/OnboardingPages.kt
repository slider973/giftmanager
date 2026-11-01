package ch.jonathanlemaine.giftmanager.ui.onboarding

import androidx.annotation.StringRes
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.Illustration

/** Une page du carrousel d'onboarding (iOS : `OnboardingView.Page`). */
data class OnboardingPageContent(
    val illustration: Illustration,
    @StringRes val title: Int,
    @StringRes val message: Int,
)

/**
 * Les quatre pages de l'onboarding. La dernière (« La surprise est garantie ») porte la connexion Google :
 * voir [ch.jonathanlemaine.giftmanager.ui.auth.SignInContent].
 */
object OnboardingPages {
    val all: List<OnboardingPageContent> = listOf(
        OnboardingPageContent(Illustration.MASCOT_FAMILY, R.string.onboarding_1_title, R.string.onboarding_1_message),
        OnboardingPageContent(Illustration.ILLUSTRATION_TRAVEL, R.string.onboarding_2_title, R.string.onboarding_2_message),
        OnboardingPageContent(Illustration.MASCOT_GIFT, R.string.onboarding_3_title, R.string.onboarding_3_message),
        OnboardingPageContent(Illustration.MASCOT_SURPRISE, R.string.sign_in_title, R.string.sign_in_message),
    )

    val lastIndex: Int get() = all.lastIndex

    fun isLast(page: Int): Boolean = page >= lastIndex

    /** Page suivante, bornée à la dernière. */
    fun next(page: Int): Int = (page + 1).coerceAtMost(lastIndex)
}
