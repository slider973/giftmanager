package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftColors
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Durée de la séquence complète avant `onFinished` (iOS : 2,2 s). */
private const val DURATION = 2.2f
/** Animations réduites : cadeau immobile, affiché brièvement. */
private const val REDUCED_MILLIS = 800L
/** Instant montré figé avec animations réduites : cadeau posé, encore fermé. */
private const val RESTING_TIME = 0.45f
/** Instant montré dans l'aperçu Android Studio : couvercle envolé, confettis en l'air. */
private const val PREVIEW_TIME = 1.5f

private const val APPEAR_END = 0.45f
private const val WIGGLE_END = 0.95f
private const val LIFT_END = 1.35f

/**
 * Animation de démarrage (`GiftOpeningSplash`) : un cadeau frétille, son couvercle s'envole, une lueur
 * sort de la boîte et des confettis jaillissent. Jouée une seule fois, puis [onFinished] est appelé.
 *
 * Port fidèle d'iOS. Une seule horloge (`withFrameNanos`) alimente un temps écoulé ; chaque image est une
 * fonction pure de ce temps ([SplashState]), rien n'est relancé — c'est ce qui manquait à l'ancienne
 * version « couvercle qui s'envole » pilotée par étapes. Le cadeau est dessiné en vectoriel (boîte et
 * couvercle séparés par construction) plutôt que de découper l'illustration `gift_red`. Le temps n'est lu
 * qu'au dessin (`Canvas`, `graphicsLayer`) : aucune recomposition par image.
 *
 * Animations réduites : cadeau fermé immobile, 0,8 s. Un toucher passe l'animation.
 */
@Composable
fun GiftOpeningSplash(onFinished: () -> Unit, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    val reduceMotion = GiftTheme.reduceMotion
    // Aperçu Android Studio : image figée, cadeau ouvert (à t = 0, rien n'est encore visible).
    val inPreview = LocalInspectionMode.current
    val finish by rememberUpdatedState(onFinished)
    val elapsed = remember {
        mutableFloatStateOf(
            when {
                inPreview -> PREVIEW_TIME
                reduceMotion -> RESTING_TIME
                else -> 0f
            },
        )
    }
    val appName = stringResource(R.string.app_name)

    LaunchedEffect(Unit) {
        if (inPreview) return@LaunchedEffect
        if (reduceMotion) {
            delay(REDUCED_MILLIS)
        } else {
            val start = withFrameNanos { it }
            while (elapsed.floatValue < DURATION) {
                elapsed.floatValue = withFrameNanos { (it - start) / 1_000_000_000f }
            }
        }
        finish()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xxl, Alignment.CenterVertically),
        modifier = modifier
            .fcScreenBackground()
            .pointerInput(Unit) { detectTapGestures { finish() } }
            .clearAndSetSemantics {
                contentDescription = appName
                role = Role.Image
            },
    ) {
        GiftScene(elapsed, size, GiftTheme.colors)
        Text(
            text = appName,
            style = GiftTheme.type.title,
            color = GiftTheme.colors.textPrimary,
            modifier = Modifier.graphicsLayer {
                val title = SplashState(elapsed.value).title
                alpha = title
                translationY = (1 - title) * 12.dp.toPx()
            },
        )
    }
}

@Composable
private fun GiftScene(elapsed: State<Float>, size: Dp, colors: GiftColors) {
    val confettiColors = remember(colors) {
        listOf(colors.heart, colors.accentAmber, colors.secondary, colors.mineFg, colors.ownedFg, colors.potFg)
    }
    Box(modifier = Modifier.size(size * 2.2f)) {
        Canvas(modifier = Modifier.size(size * 2.2f)) {
            val state = SplashState(elapsed.value)
            val u = size.toPx()
            val c = center
            drawGlow(state, c, u, colors.accentAmber)
            drawConfetti(state, c, u, confettiColors)
            drawGift(state, c, u, colors)
        }
    }
}

// MARK: Chronologie

/** Valeurs d'animation dérivées du temps écoulé. Fonction pure : aucune étape à enchaîner. */
private class SplashState(t: Float) {
    val appear: Float = outBack(progress(t, 0f, APPEAR_END))
    val wiggle: Float
    val squash: Float
    val lift: Float = outBack(progress(t, WIGGLE_END, LIFT_END))
    val glow: Float = outCubic(progress(t, WIGGLE_END, WIGGLE_END + 0.55f))
    val title: Float = outCubic(progress(t, LIFT_END - 0.15f, LIFT_END + 0.35f))
    /** Secondes écoulées depuis l'ouverture (0 avant). */
    val burst: Float = max(0f, t - WIGGLE_END)

    init {
        // Frétillement qui s'amortit, puis le cadeau se tasse juste avant de s'ouvrir.
        val w = progress(t, APPEAR_END, WIGGLE_END)
        wiggle = if (w > 0f && w < 1f) 7f * sin(2f * PI * 2.5f * w) * (1f - w) else 0f
        val squeeze = progress(t, APPEAR_END + 0.3f, WIGGLE_END)
        val release = progress(t, WIGGLE_END, WIGGLE_END + 0.2f)
        squash = 0.07f * inOut(squeeze) * (1f - outCubic(release))
    }
}

private const val PI = Math.PI.toFloat()

private fun progress(t: Float, from: Float, to: Float): Float = min(1f, max(0f, (t - from) / (to - from)))

private fun outCubic(x: Float): Float = 1f - (1f - x).pow(3)

private fun inOut(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Dépasse légèrement la cible avant de s'y poser : donne du rebond. */
private fun outBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

// MARK: Confettis

/** Un confetti : trajectoire balistique déterministe (mêmes confettis à chaque lancement). */
private class Confetto(index: Int) {
    private val jitter = sin(index * 12.9898f) * 0.5f + 0.5f // pseudo-aléatoire stable
    /** Angle de départ en radians (vers le haut : entre -π et 0). */
    val angle = -PI * (0.12f + 0.76f * index / 15f) + (jitter - 0.5f) * 0.25f
    /** Vitesse initiale, en tailles de cadeau par seconde. */
    val speed = 1.25f + jitter * 0.8f
    val length = 0.05f + jitter * 0.04f
    val spin = (if (index % 2 == 0) 1f else -1f) * (360f + jitter * 360f)

    fun position(t: Float): Offset {
        // Freinage de l'air : la vitesse horizontale s'essouffle.
        val drag = 1f - exp(-2.2f * t)
        return Offset(cos(angle) * speed * drag / 2.2f, sin(angle) * speed * t + 0.5f * GRAVITY * t * t)
    }

    fun alpha(t: Float): Float = if (t <= 0f) 0f else 1f - min(1f, max(0f, (t - 0.6f) / 0.5f))

    companion object {
        const val GRAVITY = 2.4f
        val all = List(16) { Confetto(it) }
    }
}

// MARK: Dessin

/** Lueur chaude qui monte de l'ouverture de la boîte. */
private fun DrawScope.drawGlow(state: SplashState, c: Offset, u: Float, amber: Color) {
    if (state.glow <= 0f) return
    val origin = Offset(c.x, c.y - 0.1f * u)
    val radius = 0.75f * u * (0.3f + 0.7f * state.glow)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(amber.copy(alpha = 0.45f), amber.copy(alpha = 0f)),
            center = origin,
            radius = radius,
        ),
        radius = radius,
        center = origin,
        alpha = state.glow,
    )
}

private fun DrawScope.drawConfetti(state: SplashState, c: Offset, u: Float, palette: List<Color>) {
    if (state.burst <= 0f) return
    val height = 0.028f * u
    Confetto.all.forEachIndexed { index, piece ->
        val alpha = piece.alpha(state.burst)
        if (alpha <= 0f) return@forEachIndexed
        val p = piece.position(state.burst)
        val center = Offset(c.x + p.x * u, c.y + p.y * u - 0.1f * u)
        val width = piece.length * u
        rotate(degrees = piece.spin * state.burst, pivot = center) {
            drawRoundRect(
                color = palette[index % palette.size],
                topLeft = Offset(center.x - width / 2, center.y - height / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(0.008f * u),
                alpha = alpha,
            )
        }
    }
}

private fun DrawScope.drawGift(state: SplashState, c: Offset, u: Float, colors: GiftColors) {
    val alpha = min(1f, max(0f, state.appear * 1.5f))
    if (alpha <= 0f) return
    val boxBottom = Offset(c.x, c.y + 0.48f * u)
    // Tout le cadeau : apparition avec rebond, puis frétillement autour du pied de la boîte.
    scale(scale = max(0f, state.appear), pivot = c) {
        rotate(degrees = state.wiggle, pivot = boxBottom) {
            scale(scaleX = 1f + state.squash, scaleY = 1f - state.squash, pivot = boxBottom) {
                drawBox(Offset(c.x, c.y + 0.2f * u), u, colors, alpha)
            }
            val lidCenter = Offset(
                c.x + 0.16f * u * state.lift,
                c.y - 0.17f * u - 0.62f * u * state.lift + 0.5f * u * state.squash,
            )
            rotate(degrees = -22f * state.lift, pivot = lidCenter) {
                drawLid(lidCenter, u, colors, alpha)
            }
        }
    }
}

/** Corps de la boîte, ruban vertical compris. */
private fun DrawScope.drawBox(center: Offset, u: Float, colors: GiftColors, alpha: Float) {
    val size = Size(0.76f * u, 0.56f * u)
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val corner = CornerRadius(0.07f * u)
    drawRoundRect(colors.pastelPeach, topLeft, size, corner, alpha = alpha)
    // Ombre sous le couvercle et léger dégradé pour le volume.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(colors.textPrimary.copy(alpha = 0.14f), colors.textPrimary.copy(alpha = 0.02f)),
            startY = topLeft.y,
            endY = center.y,
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        alpha = alpha,
    )
    val ribbon = 0.14f * u
    drawRect(colors.heart, Offset(center.x - ribbon / 2, topLeft.y), Size(ribbon, size.height), alpha = alpha)
}

/** Couvercle avec son ruban et son nœud : ils s'envolent ensemble. */
private fun DrawScope.drawLid(center: Offset, u: Float, colors: GiftColors, alpha: Float) {
    val size = Size(0.88f * u, 0.18f * u)
    val topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2)
    val corner = CornerRadius(0.05f * u)
    drawRoundRect(colors.pastelPeach, topLeft, size, corner, alpha = alpha)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0f)),
            startY = topLeft.y,
            endY = topLeft.y + size.height,
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        alpha = alpha,
    )
    val ribbon = 0.14f * u
    drawRect(colors.heart, Offset(center.x - ribbon / 2, topLeft.y), Size(ribbon, size.height), alpha = alpha)

    // Nœud : deux boucles inclinées et un nœud central.
    val bow = Offset(center.x, center.y - 0.15f * u)
    val stroke = 0.06f * u
    val loop = Size(0.28f * u - stroke, 0.17f * u - stroke) // tracé intérieur, comme `strokeBorder`
    listOf(-1f, 1f).forEach { side ->
        val loopCenter = Offset(bow.x + side * 0.12f * u, bow.y)
        rotate(degrees = side * 32f, pivot = loopCenter) {
            drawOval(
                color = colors.heart,
                topLeft = Offset(loopCenter.x - loop.width / 2, loopCenter.y - loop.height / 2),
                size = loop,
                alpha = alpha,
                style = Stroke(width = stroke),
            )
        }
    }
    drawCircle(colors.heart, radius = 0.06f * u, center = Offset(bow.x, bow.y + 0.04f * u), alpha = alpha)
}

@GiftPreviews
@Composable
private fun GiftOpeningSplashPreview() = PreviewFrame {
    GiftOpeningSplash(onFinished = {}, size = 100.dp)
}
