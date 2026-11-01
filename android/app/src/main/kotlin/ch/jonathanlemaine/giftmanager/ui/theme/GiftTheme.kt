package ch.jonathanlemaine.giftmanager.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

val LocalGiftColors = staticCompositionLocalOf { GiftColors.Light }
val LocalGiftTypography = staticCompositionLocalOf { GiftTypography.Default }
val LocalGiftSpacing = staticCompositionLocalOf { GiftSpacing() }
val LocalGiftRadius = staticCompositionLocalOf { GiftRadius() }

/** `true` si l'utilisateur a coupé les animations (équivalent de « Réduire les animations » d'iOS). */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Accès aux tokens du design system, à la manière de `MaterialTheme`. */
object GiftTheme {
    val colors: GiftColors
        @Composable @ReadOnlyComposable get() = LocalGiftColors.current

    val type: GiftTypography
        @Composable @ReadOnlyComposable get() = LocalGiftTypography.current

    val spacing: GiftSpacing
        @Composable @ReadOnlyComposable get() = LocalGiftSpacing.current

    val radius: GiftRadius
        @Composable @ReadOnlyComposable get() = LocalGiftRadius.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

/**
 * Lit « Échelle de durée d'animation » (Options pour les développeurs, ou « Supprimer les animations »
 * dans Accessibilité) : à 0, les animations décoratives sont figées. Suit le réglage en direct.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.applicationContext.contentResolver
    var reduce by remember { mutableStateOf(animatorScaleIsZero(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = animatorScaleIsZero(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

private fun animatorScaleIsZero(resolver: android.content.ContentResolver): Boolean =
    runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
        .getOrDefault(false)
