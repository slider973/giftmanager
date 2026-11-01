package ch.jonathanlemaine.giftmanager.ui.theme

import kotlin.math.pow

// Calculs de contraste WCAG 2.x, en Kotlin pur pour être testables sans Compose.
// Les couleurs sont des entiers ARGB (0xAARRGGBB) ; l'alpha est ignoré.

private fun channel(argb: Long, shift: Int): Double {
    val c = ((argb shr shift) and 0xFF) / 255.0
    return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

/** Luminance relative WCAG d'une couleur sRGB, de 0 (noir) à 1 (blanc). */
fun relativeLuminance(argb: Long): Double =
    0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

/** Rapport de contraste WCAG entre deux couleurs, de 1 à 21 (symétrique). */
fun contrastRatio(fg: Long, bg: Long): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}
