package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Durée d'un aller du flottement (iOS : 1,8 s, aller-retour). */
private const val FLOAT_MILLIS = 1800

/**
 * Animation de chargement (`GiftLoadingView`) : le paquet `gift_red` qui flotte doucement.
 *
 * Port fidèle d'iOS : une seule transition infinie, jamais relancée — c'est ce qui rend le mouvement
 * fluide (la version « couvercle qui s'envole », pilotée par étapes, saccadait et a été abandonnée).
 * Le paquet monte et descend en s'inclinant de ±5°, son ombre elliptique se resserre quand il monte, et
 * trois étincelles `accentAmber` scintillent en décalé. Les valeurs animées ne sont lues qu'au dessin
 * (`graphicsLayer`) : aucune recomposition par image.
 *
 * Animations réduites : pose fixe (paquet droit, étincelles visibles). TalkBack annonce [label] ou
 * « Chargement en cours » (région live polie).
 */
@Composable
fun GiftLoadingView(modifier: Modifier = Modifier, size: Dp = 120.dp, label: String? = null) {
    val description = label ?: stringResource(R.string.loading)
    val motion = rememberGiftMotion(animated = !GiftTheme.reduceMotion)
    val colors = GiftTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xl),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.6f)) {
            GroundShadow(size, colors.textPrimary, motion.float)
            repeat(3) { index -> SparkleView(index, size, colors.accentAmber, motion.sparkles[index]) }
            Image(
                painter = painterResource(Illustration.GIFT_RED.res),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        val t = motion.float.value
                        rotationZ = -5f + 10f * t
                        translationY = (size * (0.04f - 0.13f * t)).toPx()
                    },
            )
        }
        if (label != null) {
            Text(text = label, style = GiftTheme.type.caption, color = colors.textSecondary)
        }
    }
}

/** Progressions 0→1 du flottement et des trois étincelles. */
private class GiftMotion(val float: State<Float>, val sparkles: List<State<Float>>)

private class Fixed(override val value: Float) : State<Float>

@Composable
private fun rememberGiftMotion(animated: Boolean): GiftMotion {
    if (!animated) {
        // Pose figée : paquet droit à mi-hauteur, étincelles pleinement visibles.
        return remember { GiftMotion(Fixed(0.5f), List(3) { Fixed(1f) }) }
    }
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "giftLoading")
    val float = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FLOAT_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    // Scintillement décalé : chaque étincelle a sa durée et son départ, elles ne battent jamais ensemble.
    val sparkles = List(3) { index ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100 + index * 350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 300),
            ),
            label = "sparkle$index",
        )
    }
    return GiftMotion(float, sparkles)
}

/** Ombre portée floue : elle se resserre quand le paquet monte. Dégradé radial (pas de `blur`, API 26+). */
@Composable
private fun GroundShadow(size: Dp, tint: Color, float: State<Float>) {
    Canvas(modifier = Modifier.size(size * 1.6f)) {
        val t = float.value
        val width = (size * (0.6f - 0.18f * t)).toPx() + 12.dp.toPx()
        val height = (size * 0.1f).toPx() + 12.dp.toPx()
        val center = Offset(this.size.width / 2, this.size.height / 2 + (size * 0.56f).toPx())
        val brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = 0.12f), tint.copy(alpha = 0.06f), Color.Transparent),
            center = center,
            radius = width / 2,
        )
        scale(scaleX = 1f, scaleY = height / width, pivot = center) {
            drawCircle(brush = brush, radius = width / 2, center = center)
        }
    }
}

@Composable
private fun SparkleView(index: Int, size: Dp, color: Color, progress: State<Float>) {
    val (dx, dy) = when (index) {
        0 -> -0.52f to -0.3f
        1 -> 0.5f to -0.42f
        else -> 0.36f to 0.3f
    }
    Canvas(
        modifier = Modifier
            .size(size * 0.17f)
            .graphicsLayer {
                val t = progress.value
                translationX = (size * dx).toPx()
                translationY = (size * dy).toPx()
                val s = 0.35f + 0.65f * t
                scaleX = s
                scaleY = s
                alpha = 0.15f + 0.8f * t
            },
    ) {
        drawPath(sparklePath(this.size), color)
    }
}

/** Étoile à quatre branches aux pointes incurvées (même tracé qu'iOS). */
private fun sparklePath(size: Size): Path = Path().apply {
    val cx = size.width / 2
    val cy = size.height / 2
    val r = minOf(size.width, size.height) / 2
    val waist = r * 0.28f
    moveTo(cx, cy - r)
    quadraticTo(cx + waist, cy - waist, cx + r, cy)
    quadraticTo(cx + waist, cy + waist, cx, cy + r)
    quadraticTo(cx - waist, cy + waist, cx - r, cy)
    quadraticTo(cx - waist, cy - waist, cx, cy - r)
    close()
}

@GiftPreviews
@Composable
private fun GiftLoadingViewPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xl), verticalAlignment = Alignment.CenterVertically) {
        GiftLoadingView(size = 72.dp)
        GiftLoadingView(size = 44.dp, label = "Chargement…")
    }
}
