package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Ressort du retour tactile : réponse 0,25 s, amortissement 0,8 (`FCPressableStyle`). */
private val pressSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 630f)

/**
 * Carte blanche très arrondie (`fcCard`) : fond `surface`, rayon `card`, ombre unique (noir 6 %, y 2 dp,
 * flou 8 dp) et padding interne `l` par défaut. À poser **après** les modificateurs de taille.
 */
fun Modifier.fcCard(padding: Dp = 16.dp): Modifier = composed {
    val shape = GiftTheme.radius.cardShape
    this
        .dropShadow(shape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp)))
        .background(GiftTheme.colors.surface, shape)
        .padding(padding)
}

/** Fond crème (bleu nuit en sombre) plein écran (`fcScreenBackground`). */
fun Modifier.fcScreenBackground(): Modifier = composed {
    fillMaxSize().background(GiftTheme.colors.background)
}

/**
 * Retour visuel de pression partagé (`FCPressableStyle`) : léger enfoncement + opacité 0,85, sans décaler
 * la mise en page. L'échelle est coupée si les animations sont réduites. Dessine aussi un anneau `primary`
 * quand l'élément a le focus clavier / D-pad, puisque ces contrôles n'ont pas d'ondulation Material.
 */
fun Modifier.fcPressable(
    interactionSource: InteractionSource,
    shape: Shape = CapsuleShape,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val reduceMotion = GiftTheme.reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else pressSpring,
        label = "pressScale",
    )
    val alpha by animateFloatAsState(if (pressed) 0.85f else 1f, pressSpring, label = "pressAlpha")
    val ring = if (focused) Modifier.border(2.dp, GiftTheme.colors.primary, shape) else Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(ring)
}

/** `clickable` sans ondulation, avec le retour [fcPressable]. */
fun Modifier.fcClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    shape: Shape = CapsuleShape,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .fcPressable(source, shape, pressedScale)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/**
 * Marge négative (padding négatif de SwiftUI) : l'élément déborde de `top` / `end` sans occuper de place,
 * pour garder une cible tactile de 48 dp tout en alignant le glyphe sur le bord du contenu.
 */
internal fun Modifier.bleed(top: Dp = 0.dp, end: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val topPx = top.roundToPx()
    val endPx = end.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + endPx else constraints.maxWidth,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + topPx else constraints.maxHeight,
        ),
    )
    val width = (placeable.width - endPx).coerceAtLeast(0)
    val height = (placeable.height - topPx).coerceAtLeast(0)
    layout(width, height) { placeable.placeRelative(0, -topPx) }
}

/** Taille d'une icône calée sur un style de texte : elle grandit avec la police système. */
@androidx.compose.runtime.Composable
internal fun androidx.compose.ui.text.TextStyle.glyphSize(factor: Float = 1.15f): Dp =
    with(androidx.compose.ui.platform.LocalDensity.current) { (fontSize * factor).toDp() }

/** Longueur en dp multipliée par l'échelle de police, plafonnée (`@ScaledMetric` d'iOS). */
@androidx.compose.runtime.Composable
internal fun Dp.scaledByFont(maxFactor: Float = 1.5f): Dp =
    this * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, maxFactor)

/**
 * Désature le rendu de l'élément (`.grayscale(0.7)` d'iOS) : archives, événements passés. Le texte reste à
 * pleine opacité — une opacité réduite casserait le contraste AA. [saturation] 0 = gris, 1 = intact.
 * Dessine dans un calque, ombre de carte comprise ; toutes versions d'Android.
 */
fun Modifier.fcDesaturated(saturation: Float = 0.3f): Modifier = drawWithCache {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
    // Le calque déborde un peu pour garder l'ombre portée de `fcCard`.
    val bleed = 12.dp.toPx()
    val bounds = Rect(-bleed, -bleed, size.width + bleed, size.height + bleed)
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, paint)
            drawContent()
            canvas.restore()
        }
    }
}
