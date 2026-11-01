package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

private enum class RemotePhase { LOADING, MISSING, FAILED, SUCCESS }

/**
 * Image distante avec placeholder doux (`RemoteImage`) : cadeau sur fond pastel.
 *
 * Remplit le cadre fourni par l'appelant et s'y découpe : poser la taille et le `clip` sur [modifier].
 * États : chargement (cadeau qui respire, figé si animations réduites), succès (fondu), échec ou URL absente
 * (cadeau + pastille « ! » pour l'échec). [placeholderSeed] (le titre du cadeau) choisit un pastel stable :
 * une liste sans photos alterne les teintes ; sans graine, pêche.
 *
 * [contentDescription] `null` = image décorative (la carte porte déjà le titre).
 */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderSeed: String? = null,
    contentDescription: String? = null,
) {
    Box(modifier = modifier.clipToBounds()) {
        if (url.isNullOrBlank()) {
            RemoteImagePlaceholder(RemotePhase.MISSING, placeholderSeed, contentDescription != null)
            return@Box
        }
        var phase by remember(url) { mutableStateOf(RemotePhase.LOADING) }
        if (phase != RemotePhase.SUCCESS) {
            RemoteImagePlaceholder(phase, placeholderSeed, contentDescription != null)
        }
        val context = LocalContext.current
        val reduceMotion = GiftTheme.reduceMotion
        val request = remember(url, reduceMotion) {
            ImageRequest.Builder(context).data(url).crossfade(if (reduceMotion) 0 else 250).build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                phase = when (state) {
                    is AsyncImagePainter.State.Success -> RemotePhase.SUCCESS
                    is AsyncImagePainter.State.Error -> RemotePhase.FAILED
                    else -> RemotePhase.LOADING
                }
            },
        )
    }
}

@Composable
private fun RemoteImagePlaceholder(phase: RemotePhase, seed: String?, announce: Boolean) {
    val colors = GiftTheme.colors
    val fill = seed?.let(colors::pastelFor) ?: colors.pastelPeach
    val description = stringResource(
        when (phase) {
            RemotePhase.LOADING -> R.string.image_loading
            RemotePhase.FAILED -> R.string.image_failed
            else -> R.string.image_missing
        },
    )
    val breathing = phase == RemotePhase.LOADING && !GiftTheme.reduceMotion
    val iconAlpha = if (breathing) {
        val transition = rememberInfiniteTransition(label = "placeholder")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        null
    }
    val glyphTint = colors.textPrimary.copy(alpha = if (phase == RemotePhase.FAILED) 0.18f else 0.28f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fill)
            .then(if (announce) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier.clearAndSetSemantics { }),
    ) {
        // Le symbole reste discret : un tiers de la vignette, entre 14 et 48 dp (grande fiche cadeau).
        Icon(
            imageVector = GiftIcons.Gift,
            contentDescription = null,
            tint = glyphTint,
            modifier = Modifier
                .proportionalSquare(fraction = 0.34f, minDp = 14f, maxDp = 48f)
                .align(Alignment.Center)
                .graphicsLayer { alpha = iconAlpha?.value ?: 1f },
        )
        if (phase == RemotePhase.FAILED) {
            Icon(
                imageVector = GiftIcons.Error,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .proportionalSquare(fraction = 0.15f, minDp = 10f, maxDp = 22f, insetFraction = 0.06f, minInsetDp = 4f),
            )
        }
    }
}

/**
 * Carré dont le côté est une fraction du plus petit côté disponible, borné en dp ; [insetFraction] le
 * décolle des bords (pastille d'échec). Sans sous-composition : compatible avec les mesures intrinsèques.
 */
private fun Modifier.proportionalSquare(
    fraction: Float,
    minDp: Float,
    maxDp: Float,
    insetFraction: Float = 0f,
    minInsetDp: Float = 0f,
): Modifier = layout { measurable, constraints ->
    val available = minOf(
        if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE,
        if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE,
    ).takeIf { it != Int.MAX_VALUE } ?: maxDp.dp.roundToPx()
    val side = (available * fraction).toInt().coerceIn(minDp.dp.roundToPx(), maxDp.dp.roundToPx())
    val inset = maxOf((available * insetFraction).toInt(), minInsetDp.dp.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side + inset, side + inset) { placeable.place(0, 0) }
}

@GiftPreviews
@Composable
private fun RemoteImagePreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
        val thumb = Modifier.size(76.dp).clip(GiftTheme.radius.thumbShape)
        RemoteImage(url = null, modifier = thumb)
        RemoteImage(url = null, placeholderSeed = "LEGO Technic", modifier = thumb)
        RemoteImage(url = "https://invalid.invalid/x.png", placeholderSeed = "Casque", modifier = thumb)
    }
}
