package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.NunitoFamily
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * Avatar rond pastel avec émoji ou initiale (`ChildAvatar`), ou photo si [photoUrl] est fourni (Coil).
 * Pendant le chargement ou en cas d'échec de la photo, l'avatar pastel reste visible.
 *
 * [colorName] : `pastelPink`, `pastelMint`… ; absent ou inconnu → pastel stable dérivé du prénom.
 * TalkBack lit le prénom. La taille est fixe (elle ne suit pas la police) : c'est une forme, pas du texte.
 */
@Composable
fun ChildAvatar(
    name: String,
    emoji: String?,
    colorName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    photoUrl: String? = null,
) {
    val colors = GiftTheme.colors
    val fill = colors.pastel(colorName) ?: colors.pastelFor(name)
    var photoLoaded by remember(photoUrl) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .clearAndSetSemantics { contentDescription = name },
    ) {
        if (!photoLoaded) {
            if (!emoji.isNullOrBlank()) {
                Text(text = emoji, style = TextStyle(fontSize = size.fixedSp(0.52f)))
            } else {
                Text(
                    text = initialOf(name),
                    color = colors.textPrimary,
                    style = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold, fontSize = size.fixedSp(0.42f)),
                )
            }
        }
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                onState = { photoLoaded = it is AsyncImagePainter.State.Success },
            )
        }
    }
}

/** Initiale en majuscule, « ? » pour un prénom vide. */
internal fun initialOf(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

/** Taille de police proportionnelle à une forme de taille fixe : ne suit pas l'échelle de police. */
@Composable
private fun Dp.fixedSp(factor: Float) = with(LocalDensity.current) { (this@fixedSp * factor).toSp() }

/**
 * Avatars qui se chevauchent, suivis d'un compteur « +N » (`AvatarStack`).
 * TalkBack : « Claire, Paul, Mamie et 2 autres ».
 */
@Composable
fun AvatarStack(names: List<String>, modifier: Modifier = Modifier, maxVisible: Int = 5, size: Dp = 32.dp) {
    val colors = GiftTheme.colors
    val visible = names.take(maxVisible.coerceAtLeast(0))
    val overflow = (names.size - visible.size).coerceAtLeast(0)
    val ring = maxOf(1.5.dp, size * 0.06f)
    val listed = visible.joinToString(", ")
    val description = when {
        names.isEmpty() -> stringResource(R.string.avatars_none)
        overflow > 0 -> pluralStringResource(R.plurals.avatars_overflow, overflow, listed, overflow)
        else -> listed
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(-size * 0.28f),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        visible.forEach { name ->
            ChildAvatar(
                name = name,
                emoji = null,
                colorName = null,
                size = size,
                modifier = Modifier.border(ring, colors.surface, CircleShape),
            )
        }
        if (overflow > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .background(colors.background, CircleShape)
                    .border(ring, colors.surface, CircleShape),
            ) {
                Text(
                    text = "+$overflow",
                    color = colors.textSecondary,
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = size.fixedSp(0.36f)).tabular(),
                    maxLines = 1,
                )
            }
        }
    }
}

@GiftPreviews
@Composable
private fun ChildAvatarPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
        ChildAvatar("Léo", "🦊", "pastelPeach", size = 56.dp)
        ChildAvatar("Emma", null, "pastelPink", size = 56.dp)
        ChildAvatar("Noé", null, null)
        ChildAvatar("", null, "pastelLavender")
    }
    AvatarStack(listOf("Jonathan", "Claire", "Paul", "Mamie", "Papi", "Léo", "Emma", "Noé"))
    AvatarStack(listOf("Léo", "Emma", "Noé"), size = 24.dp)
}
