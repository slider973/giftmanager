package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.ui.theme.GiftColors
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Pastel de la vignette : menthe pour Noël, rose pour un anniversaire, pêche sinon. */
fun GiftEventKind.tile(colors: GiftColors): Color = when (this) {
    GiftEventKind.CHRISTMAS -> colors.pastelMint
    GiftEventKind.BIRTHDAY -> colors.pastelPink
    GiftEventKind.OTHER -> colors.pastelPeach
}

private val GiftEventKind.nameRes: Int
    get() = when (this) {
        GiftEventKind.CHRISTMAS -> R.string.event_kind_christmas
        GiftEventKind.BIRTHDAY -> R.string.event_kind_birthday
        GiftEventKind.OTHER -> R.string.event_kind_other
    }

/**
 * Carte d'événement (`EventRow`, écran 2) : vignette illustrée, titre, date · sous-titre, participants,
 * chevron. La carte entière est la cible ([onClick]) ; TalkBack la lit d'un bloc.
 */
@Composable
fun EventRow(
    title: String,
    dateText: String,
    subtitle: String?,
    kind: GiftEventKind,
    avatarNames: List<String>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val meta = listOfNotNull(dateText, subtitle).filter { it.isNotEmpty() }.joinToString(" · ")
    val interaction = if (onClick != null) {
        Modifier.fcClickable(
            onClick = onClick,
            onClickLabel = stringResource(R.string.event_open),
            shape = GiftTheme.radius.cardShape,
            pressedScale = 0.98f,
        )
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .fcCard(padding = spacing.m)
            .semantics(mergeDescendants = true) { },
    ) {
        EventKindTile(kind = kind, size = 56.dp.scaledByFont(1.3f))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = title, style = type.headline, color = colors.textPrimary)
            Text(text = meta, style = type.caption, color = colors.textSecondary)
            if (avatarNames.isNotEmpty()) {
                AvatarStack(avatarNames, maxVisible = 3, size = 24.dp, modifier = Modifier.padding(top = spacing.xs / 2))
            }
        }
        Icon(
            imageVector = GiftIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(type.callout.glyphSize(1.4f)),
        )
    }
}

/** Vignette carrée arrondie illustrant le type d'événement (`EventKindTile`). TalkBack : « Noël »… */
@Composable
fun EventKindTile(kind: GiftEventKind, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val label = stringResource(kind.nameRes)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .background(kind.tile(GiftTheme.colors), GiftTheme.radius.thumbShape)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        when (kind) {
            GiftEventKind.CHRISTMAS -> EmojiGlyph("🎄", size)
            GiftEventKind.BIRTHDAY -> EmojiGlyph("🎂", size)
            GiftEventKind.OTHER -> Image(
                painter = painterResource(Illustration.GIFT_RED.res),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(size * 0.18f),
            )
        }
    }
}

/** Émoji à la moitié de la vignette ; la vignette ayant déjà grandi avec la police, on ne la rescale pas. */
@Composable
private fun EmojiGlyph(emoji: String, tile: Dp) {
    val fontSize = with(LocalDensity.current) { (tile * 0.5f).toSp() }
    Text(text = emoji, style = TextStyle(fontSize = fontSize))
}

@GiftPreviews
@Composable
private fun EventRowPreview() = PreviewFrame {
    EventRow("Anniversaire de Léo", "12 mars 2026", "8 ans", GiftEventKind.BIRTHDAY, listOf("Claire", "Paul", "Mamie"))
    EventRow("Noël 2026", "25 décembre 2026", null, GiftEventKind.CHRISTMAS, listOf("Léo", "Emma", "Noé", "Papi", "Mamie"))
    EventRow("Fête de fin d'année", "3 juin 2026", "Chez Mamie", GiftEventKind.OTHER, emptyList())
}
