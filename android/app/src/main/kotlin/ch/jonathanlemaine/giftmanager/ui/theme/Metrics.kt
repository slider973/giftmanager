package ch.jonathanlemaine.giftmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Grille d'espacement sur 4 dp (`Spacing`). Marge d'écran : `xl` ; entre cartes : `m`. */
@Immutable
data class GiftSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 32.dp,
)

/** Rayons d'angle (`Radius`). Boutons et badges : capsule ([CapsuleShape]). */
@Immutable
data class GiftRadius(
    val card: Dp = 20.dp,
    val thumb: Dp = 14.dp,
    val field: Dp = 14.dp,
) {
    val cardShape: RoundedCornerShape get() = RoundedCornerShape(card)
    val thumbShape: RoundedCornerShape get() = RoundedCornerShape(thumb)
    val fieldShape: RoundedCornerShape get() = RoundedCornerShape(this.field)
}

/** Capsule : boutons pilule, badges, jauges. */
val CapsuleShape: RoundedCornerShape = RoundedCornerShape(percent = 50)

/**
 * Tailles minimales d'interaction (`HitTarget`). Sur Android, la cible minimale est celle de Material
 * (48 dp) et non celle d'Apple (44 pt).
 */
object HitTarget {
    val minimum: Dp = 48.dp
    /** Hauteur des boutons pilule pleine largeur. */
    val button: Dp = 52.dp
}
