package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Ton d'un [FCNotice]. */
enum class FCNoticeTone {
    /** Lavande (`ownedBg`/`ownedFg`) : rappel du mode surprise (« Enfants protégés »). */
    SURPRISE,

    /** Carte claire bordée : états d'archive, précisions. */
    NEUTRAL,

    /** Rose « Déjà pris » (`takenBg`/`takenFg`) : alerte douce. */
    WARNING,
}

/**
 * Encart d'information discret : icône + phrase sur fond teinté (`FCNotice`). Paires couleur / fond AA
 * dans les deux apparences (mêmes tokens que les badges). L'icône est décorative : seule la phrase est lue.
 */
@Composable
fun FCNotice(icon: ImageVector, text: String, modifier: Modifier = Modifier, tone: FCNoticeTone = FCNoticeTone.NEUTRAL) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val shape = GiftTheme.radius.fieldShape
    val (foreground, background) = when (tone) {
        FCNoticeTone.SURPRISE -> colors.ownedFg to colors.ownedBg
        FCNoticeTone.NEUTRAL -> colors.textSecondary to colors.surface
        FCNoticeTone.WARNING -> colors.takenFg to colors.takenBg
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(if (tone == FCNoticeTone.NEUTRAL) Modifier.border(1.dp, colors.separator, shape) else Modifier)
            .padding(horizontal = GiftTheme.spacing.m, vertical = GiftTheme.spacing.s + 2.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        // Calée sur la première ligne : hauteur de ligne de la légende.
        val line = type.caption.lineHeight
        val lineDp = with(androidx.compose.ui.platform.LocalDensity.current) { line.toDp() }
        val glyph = type.caption.glyphSize()
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.padding(top = ((lineDp - glyph) / 2).coerceAtLeast(0.dp)).size(glyph),
        )
        Text(text = text, style = type.caption, color = foreground, modifier = Modifier.weight(1f))
    }
}

@GiftPreviews
@Composable
private fun FCNoticePreview() = PreviewFrame {
    FCNotice(GiftIcons.EyeSlash, "Mode surprise : tu ne vois pas ce qui a été réservé pour tes enfants.", tone = FCNoticeTone.SURPRISE)
    FCNotice(GiftIcons.Archive, "Événement passé : les listes sont archivées en lecture seule.")
    FCNotice(GiftIcons.Warning, "Les parents l'ont noté comme déjà possédé.", tone = FCNoticeTone.WARNING)
}
