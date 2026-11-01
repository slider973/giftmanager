package ch.jonathanlemaine.giftmanager.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * Alerte de l'app, alignée sur l'alerte « Oups » de la connexion (#70) : carte `surface` au rayon `card`,
 * titre arrondi `title`, message `callout` / `textSecondary`, boutons texte `primary`.
 *
 * [destructive] colore l'action en `takenFg` (le rouge système passe sous AA, comme sur iOS). [content]
 * ajoute un champ sous le message (« Rejoindre une famille »). Le corps défile aux grandes polices.
 */
@Composable
fun GiftAlertDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = GiftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            DialogButton(confirmLabel, onConfirm, if (destructive) colors.takenFg else colors.primary, confirmEnabled, emphasized = destructive)
        },
        dismissButton = dismissLabel?.let { label -> { DialogButton(label, onDismiss, colors.primary) } },
        title = { Text(text = title, style = GiftTheme.type.title, color = colors.textPrimary) },
        text = if (message == null && content == null) {
            null
        } else {
            {
                Column(
                    verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.l),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    message?.let { Text(text = it, style = GiftTheme.type.callout, color = colors.textSecondary) }
                    content?.invoke(this)
                }
            }
        },
        shape = GiftTheme.radius.cardShape,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
    )
}

@Composable
private fun DialogButton(label: String, onClick: () -> Unit, color: Color, enabled: Boolean = true, emphasized: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = HitTarget.minimum)) {
        Text(
            text = label,
            style = GiftTheme.type.callout.copy(fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium),
            color = if (enabled) color else GiftTheme.colors.textSecondary,
        )
    }
}

/** Alerte d'erreur « Oups » + message de [error] (iOS : `appState.errorMessage`). Rien si [error] est `null`. */
@Composable
fun GiftErrorDialog(error: GiftError?, onDismiss: () -> Unit) {
    if (error == null) return
    GiftAlertDialog(
        title = stringResource(R.string.error_dialog_title),
        message = stringResource(error.messageRes),
        confirmLabel = stringResource(R.string.error_dialog_ok),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    )
}

@GiftPreviews
@Composable
private fun GiftErrorDialogPreview() = PreviewFrame {
    GiftErrorDialog(GiftError.Network, onDismiss = {})
}
