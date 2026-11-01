package ch.jonathanlemaine.giftmanager.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/** Bouton de tête d'une [GiftTopBar] : flèche de retour (écran poussé) ou croix (formulaire plein écran). */
enum class TopBarNavigation { BACK, CLOSE }

/**
 * Barre du haut des écrans poussés (iOS : barre de navigation inline), à la manière d'un `TopAppBar` M3
 * « small » aux couleurs de l'app : fond `background`, sans élévation.
 *
 * - [navigation] : ← « Retour » (fiche) ou ✕ « Annuler » (formulaire plein écran, idiome Material).
 * - [title] : titre `title` arrondi, deux lignes au plus (grandes polices), titre TalkBack. Avec
 *   [titleVisible] = `false`, il est masqué et retiré de TalkBack : la fiche l'affiche en grand dans le
 *   contenu et ne le fait apparaître ici qu'une fois ce grand titre parti sous la barre.
 * - [showDivider] : filet `separator` quand le contenu défile dessous (seul « relief » permis, pas d'ombre).
 * - [actions] : actions de queue (« Modifier »).
 * - [navigationLabel] : libellé TalkBack du bouton de tête, si « Retour » / « Annuler » ne convient pas
 *   (« Fermer » d'une session qui n'a rien à annuler).
 */
@Composable
fun GiftTopBar(
    navigation: TopBarNavigation,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleVisible: Boolean = true,
    showDivider: Boolean = false,
    navigationLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val reduceMotion = GiftTheme.reduceMotion
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(150),
        label = "topBarTitle",
    )
    val dividerAlpha by animateFloatAsState(
        targetValue = if (showDivider) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(150),
        label = "topBarDivider",
    )
    val navigationText = navigationLabel
        ?: stringResource(if (navigation == TopBarNavigation.BACK) R.string.back else R.string.cancel)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TopBarHeight)
                .padding(horizontal = spacing.xs),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HitTarget.minimum)
                    .fcClickable(onClick = onNavigate, shape = CircleShape, pressedScale = 0.9f)
                    .semantics {
                        contentDescription = navigationText
                        // Premier élément lu à l'ouverture de l'écran, avant le titre.
                        traversalIndex = -1f
                    },
            ) {
                Icon(
                    imageVector = if (navigation == TopBarNavigation.BACK) GiftIcons.Back else GiftIcons.Close,
                    contentDescription = null,
                    tint = colors.textPrimary,
                )
            }
            Box(modifier = Modifier.weight(1f).padding(horizontal = spacing.xs)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = GiftTheme.type.title,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .alpha(titleAlpha)
                            .then(if (titleVisible) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics { }),
                    )
                }
            }
            actions()
        }
        HorizontalDivider(thickness = 1.dp, color = colors.separator, modifier = Modifier.alpha(dividerAlpha))
    }
}

/** Hauteur minimale de la barre (Material : 64 dp). */
private val TopBarHeight = 64.dp

/** Action texte de queue d'une [GiftTopBar] (« Modifier ») : `primary` semi-gras, cible ≥ 48 dp. */
@Composable
fun TopBarTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = HitTarget.minimum)
            .fcClickable(onClick = onClick, shape = GiftTheme.radius.fieldShape, pressedScale = 1f)
            .padding(horizontal = GiftTheme.spacing.m),
    ) {
        Text(text = text, style = GiftTheme.type.headline, color = GiftTheme.colors.primary, maxLines = 1)
    }
}

/**
 * Écran poussé : [topBar] fixe en haut (hors défilement, retour toujours accessible), puis [content].
 * Pose les marges système du haut et des côtés ; le contenu (souvent un [ScreenColumn]) ne reçoit plus
 * que le bas et le clavier, sans double comptage.
 */
@Composable
fun TopBarScreen(topBar: @Composable () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        topBar()
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@GiftPreviews
@Composable
private fun GiftTopBarPreview() = GiftManagerTheme {
    Column(modifier = Modifier.fcScreenBackground()) {
        GiftTopBar(TopBarNavigation.CLOSE, onNavigate = {}, title = "Nouvelle liste d'adulte")
        GiftTopBar(TopBarNavigation.BACK, onNavigate = {}, title = "Anniversaire de Léo", showDivider = true) {
            TopBarTextAction("Modifier", onClick = {})
        }
    }
}
