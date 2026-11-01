package ch.jonathanlemaine.giftmanager.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Couleurs du design system (`Color.Theme` sur iOS), résolues pour une apparence.
 * Construites depuis [ThemeTokens] : aucune valeur hexadécimale ne doit apparaître dans un écran.
 * Accès : `GiftTheme.colors`.
 */
@Immutable
class GiftColors(
    val background: Color,
    val surface: Color,
    val separator: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val heart: Color,
    /**
     * Accent chaud. En clair, 4,11:1 seulement sur `background` : jamais pour du texte posé sur le fond,
     * uniquement en fond de pastille (texte `onPrimary`) ou pour une icône/forme (≥ 3:1).
     */
    val accentAmber: Color,
    val availableBg: Color,
    val availableFg: Color,
    val takenBg: Color,
    val takenFg: Color,
    val mineBg: Color,
    val mineFg: Color,
    val ownedBg: Color,
    val ownedFg: Color,
    val potBg: Color,
    val potFg: Color,
    val pastelPink: Color,
    val pastelMint: Color,
    val pastelBlue: Color,
    val pastelPeach: Color,
    val pastelLavender: Color,
    val isDark: Boolean,
) {
    fun pastel(pastel: Pastel): Color = when (pastel) {
        Pastel.PINK -> pastelPink
        Pastel.MINT -> pastelMint
        Pastel.BLUE -> pastelBlue
        Pastel.PEACH -> pastelPeach
        Pastel.LAVENDER -> pastelLavender
    }

    /** `pastelMint` ou `mint` ; `null` si inconnu (`Color.Theme.pastel(named:)`). */
    fun pastel(named: String?): Color? = Pastel.named(named)?.let(::pastel)

    /** Pastel stable dérivé d'une chaîne, prénom ou titre (`Color.Theme.pastel(for:)`). */
    fun pastelFor(seed: String): Color = pastel(Pastel.forSeed(seed))

    companion object {
        fun from(tokens: ThemeTokens, isDark: Boolean): GiftColors = GiftColors(
            background = Color(tokens.background),
            surface = Color(tokens.surface),
            separator = Color(tokens.separator),
            primary = Color(tokens.primary),
            onPrimary = Color(tokens.onPrimary),
            secondary = Color(tokens.secondary),
            textPrimary = Color(tokens.textPrimary),
            textSecondary = Color(tokens.textSecondary),
            heart = Color(tokens.heart),
            accentAmber = Color(tokens.accentAmber),
            availableBg = Color(tokens.availableBg),
            availableFg = Color(tokens.availableFg),
            takenBg = Color(tokens.takenBg),
            takenFg = Color(tokens.takenFg),
            mineBg = Color(tokens.mineBg),
            mineFg = Color(tokens.mineFg),
            ownedBg = Color(tokens.ownedBg),
            ownedFg = Color(tokens.ownedFg),
            potBg = Color(tokens.potBg),
            potFg = Color(tokens.potFg),
            pastelPink = Color(tokens.pastelPink),
            pastelMint = Color(tokens.pastelMint),
            pastelBlue = Color(tokens.pastelBlue),
            pastelPeach = Color(tokens.pastelPeach),
            pastelLavender = Color(tokens.pastelLavender),
            isDark = isDark,
        )

        val Light: GiftColors = from(ThemeTokens.Light, isDark = false)
        val Dark: GiftColors = from(ThemeTokens.Dark, isDark = true)
    }
}

/**
 * Projection sur les rôles Material 3, pour que les composants M3 (Slider, Switch, CircularProgressIndicator,
 * sélection de texte…) prennent l'identité de l'app. Pas de couleurs dynamiques Material You : la palette est
 * celle de la marque, identique à iOS. Toutes les surfaces sont unies (pas de teinte d'élévation).
 */
fun GiftColors.toColorScheme(): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = mineBg,
        onPrimaryContainer = mineFg,
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = onPrimary,
        secondaryContainer = availableBg,
        onSecondaryContainer = availableFg,
        tertiary = accentAmber,
        onTertiary = onPrimary,
        tertiaryContainer = potBg,
        onTertiaryContainer = potFg,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = background,
        onSurfaceVariant = textSecondary,
        // Teinte d'élévation neutralisée : une seule profondeur, l'ombre des cartes, comme sur iOS.
        surfaceTint = surface,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = takenFg,
        onError = onPrimary,
        errorContainer = takenBg,
        onErrorContainer = takenFg,
        outline = textSecondary,
        outlineVariant = separator,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = background,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
    )
