package ch.jonathanlemaine.giftmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ch.jonathanlemaine.giftmanager.R

/**
 * Nunito, police arrondie variable (axe `wght`) : l'équivalent Android de SF Pro Rounded, réservé aux
 * deux niveaux de titre. Une entrée par graisse, chacune positionnant l'axe sur sa valeur.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val NunitoFamily: FontFamily = FontFamily(
    listOf(FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map { weight ->
        Font(
            resId = R.font.nunito,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * Échelle typographique (`Font.Theme`). Tailles toujours en `sp` : elles suivent la taille de police
 * système. Titres en Nunito, le reste en police système (Roboto) pour la lisibilité.
 * Interlignes repris des styles Dynamic Type d'iOS.
 */
@Immutable
data class GiftTypography(
    /** 34 sp Bold arrondi — « Notre famille », « Gift Manager ». */
    val largeTitle: TextStyle,
    /** 22 sp Bold arrondi — titres secondaires, prénom de l'enfant, titre d'état vide. */
    val title: TextStyle,
    /** 17 sp SemiBold — nom d'un cadeau, d'un événement, boutons. */
    val headline: TextStyle,
    /** 15 sp Regular — textes courants. */
    val body: TextStyle,
    /** 16 sp Regular — onglets, liens texte, lignes de boutique. */
    val callout: TextStyle,
    /** 12 sp Regular — prix, boutique, métadonnées. */
    val caption: TextStyle,
    /** 12 sp SemiBold — badges, libellés de champs, compteurs. */
    val captionBold: TextStyle,
) {
    companion object {
        val Default = GiftTypography(
            largeTitle = TextStyle(
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 41.sp,
            ),
            title = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
            headline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
            body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
            callout = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp),
            caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            captionBold = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

/** Chiffres tabulaires (`.monospacedDigit()` sur iOS) : prix, montants, compteurs. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Projection sur les rôles typographiques Material 3, pour les composants M3. */
fun GiftTypography.toMaterialTypography(): Typography = Typography(
    displaySmall = largeTitle,
    headlineLarge = largeTitle,
    headlineMedium = title,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = headline,
    titleSmall = captionBold.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = callout,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = headline,
    labelMedium = captionBold,
    labelSmall = caption,
)
