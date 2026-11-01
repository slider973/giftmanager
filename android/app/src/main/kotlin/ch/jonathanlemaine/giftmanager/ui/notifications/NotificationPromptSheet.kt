package ch.jonathanlemaine.giftmanager.ui.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.push.NotificationPermission
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Feuille d'explication avant la demande d'autorisation des notifications (#76), à la première arrivée sur l'écran
 * principal : loutre au cadeau, promesse, trois avantages (rappels d'achats, anniversaires, nouvelles envies), rappel
 * que la surprise est préservée, puis « Activer les notifications » / « Plus tard ».
 *
 * [permission] [NotificationPermission.PermanentlyDenied] : le système ne demandera plus ; le message le dit et le
 * CTA devient « Ouvrir les réglages » ([onOpenSettings]). Glisser la feuille ou Retour = « Plus tard » ([onLater]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPromptSheet(
    permission: NotificationPermission,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        NotificationPromptContent(permission, onEnable, onOpenSettings, onLater)
    }
}

/** Contenu de la feuille, séparé pour les aperçus (une `ModalBottomSheet` ne s'affiche pas en aperçu). */
@Composable
internal fun NotificationPromptContent(
    permission: NotificationPermission,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val denied = permission == NotificationPermission.PermanentlyDenied
    // Paysage ou petit écran : la loutre cède la place, les avantages et les boutons restent sans défilement.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val showMascot = windowHeight == 0.dp || windowHeight >= 560.dp // 0 : fenêtre pas encore mesurée (aperçus)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.xl, end = spacing.xl, bottom = spacing.xl),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            if (showMascot) {
                Image(
                    painter = painterResource(Illustration.MASCOT_GIFT.res),
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = spacing.xs).size(132.dp),
                )
            }
            Text(
                text = stringResource(R.string.notif_prompt_title),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(if (denied) R.string.notif_prompt_message_denied else R.string.notif_prompt_message),
                style = type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.l),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().fcCard(padding = spacing.l),
        ) {
            BenefitRow(
                icon = GiftIcons.Bag,
                tint = colors.mineFg,
                disc = colors.pastelBlue,
                title = stringResource(R.string.notif_prompt_purchases_title),
                subtitle = stringResource(R.string.notif_prompt_purchases_subtitle),
            )
            BenefitRow(
                icon = GiftIcons.Cake,
                tint = colors.takenFg,
                disc = colors.pastelPink,
                title = stringResource(R.string.notif_prompt_birthdays_title),
                subtitle = stringResource(R.string.notif_prompt_birthdays_subtitle),
            )
            BenefitRow(
                icon = GiftIcons.Sparkles,
                tint = colors.availableFg,
                disc = colors.pastelMint,
                title = stringResource(R.string.notif_prompt_news_title),
                subtitle = stringResource(R.string.notif_prompt_news_subtitle),
            )
        }

        FCNotice(
            icon = GiftIcons.EyeSlash,
            text = stringResource(R.string.notif_prompt_surprise),
            tone = FCNoticeTone.SURPRISE,
            modifier = Modifier.widthIn(max = 480.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(top = spacing.xs),
        ) {
            if (denied) {
                PrimaryButton(
                    text = stringResource(R.string.notif_prompt_open_settings),
                    onClick = onOpenSettings,
                    trailingIcon = GiftIcons.OpenExternal,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.notif_prompt_enable),
                    onClick = onEnable,
                    leadingIcon = GiftIcons.Bell,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextLinkButton(text = stringResource(R.string.notif_prompt_later), onClick = onLater)
        }
    }
}

/**
 * Un avantage : icône sur disque pastel (paires de `FeaturePill`), titre `headline`, précision `caption`.
 * Un seul nœud TalkBack (titre puis précision) ; l'icône est décorative. Le disque suit la police, plafonné.
 */
@Composable
private fun BenefitRow(icon: ImageVector, tint: Color, disc: Color, title: String, subtitle: String) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.4f)
    val discSize = 44.dp * fontScale
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(discSize).background(disc, CircleShape)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(discSize * 0.5f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = type.headline, color = colors.textPrimary)
            Text(subtitle, style = type.caption, color = colors.textSecondary)
        }
    }
}

// region Aperçus

@GiftPreviews
@Composable
private fun NotificationPromptCanAskPreview() = GiftManagerTheme {
    Column(Modifier.fillMaxWidth().background(GiftTheme.colors.background).padding(top = GiftTheme.spacing.l)) {
        NotificationPromptContent(NotificationPermission.CanAsk, onEnable = {}, onOpenSettings = {}, onLater = {})
    }
}

@GiftPreviews
@Composable
private fun NotificationPromptDeniedPreview() = GiftManagerTheme {
    Column(Modifier.fillMaxWidth().background(GiftTheme.colors.background).padding(top = GiftTheme.spacing.l)) {
        NotificationPromptContent(NotificationPermission.PermanentlyDenied, onEnable = {}, onOpenSettings = {}, onLater = {})
    }
}

// endregion
