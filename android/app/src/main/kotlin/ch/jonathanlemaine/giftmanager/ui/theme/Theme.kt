package ch.jonathanlemaine.giftmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Thème de l'app : tokens Gift Manager (`GiftTheme`) et leur projection Material 3 (`MaterialTheme`).
 * Pas de couleurs dynamiques : la palette de marque est la même qu'iOS.
 */
@Composable
fun GiftManagerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) GiftColors.Dark else GiftColors.Light
    val type = GiftTypography.Default
    val colorScheme = remember(colors) { colors.toColorScheme() }
    val reduceMotion = rememberReduceMotion()
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.primary, backgroundColor = colors.primary.copy(alpha = 0.3f))
    }

    CompositionLocalProvider(
        LocalGiftColors provides colors,
        LocalGiftTypography provides type,
        LocalGiftSpacing provides GiftSpacing(),
        LocalGiftRadius provides GiftRadius(),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = remember(type) { type.toMaterialTypography() }) {
            CompositionLocalProvider(
                LocalContentColor provides colors.textPrimary,
                LocalTextSelectionColors provides selection,
                content = content,
            )
        }
    }
}
