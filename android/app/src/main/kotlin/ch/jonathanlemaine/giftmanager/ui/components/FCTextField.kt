package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * Champ arrondi `surface` avec libellé visible au-dessus — jamais de placeholder seul (`FCTextField`).
 *
 * [isLabelHidden] masque le libellé à l'écran (champ d'URL de la maquette) tout en le gardant pour
 * TalkBack. Bordure `primary` au focus ; bouton « Effacer » (48 dp) quand le champ a le focus et un contenu.
 */
@Composable
fun FCTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: String? = null,
    isLabelHidden: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val shape = GiftTheme.radius.fieldShape
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val borderColor by animateColorAsState(if (focused) colors.primary else colors.separator, tween(150), label = "border")
    val borderWidth by animateDpAsState(if (focused) 1.5.dp else 1.dp, tween(150), label = "borderWidth")

    // Libellé, icône, placeholder et bouton « Effacer » vivent dans la décoration du champ : TalkBack les
    // fusionne avec le nœud éditable (« Prénom, Léo, champ de modification »), comme les champs Material.
    // Libellé masqué : il ne reste que la description du champ.
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = type.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(if (isLabelHidden) Modifier.semantics { contentDescription = label } else Modifier),
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                if (!isLabelHidden) {
                    Text(text = label, style = type.captionBold, color = colors.textSecondary)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HitTarget.minimum.scaledByFont())
                        .background(colors.surface, shape)
                        .border(borderWidth, borderColor, shape)
                        .padding(start = spacing.l, end = if (focused && value.isNotEmpty()) 0.dp else spacing.l),
                ) {
                    leadingIcon?.let {
                        Icon(it, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.callout.glyphSize(1.25f)))
                    }
                    Box(modifier = Modifier.weight(1f).padding(vertical = spacing.m)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(text = placeholder, style = type.body, color = colors.textSecondary, maxLines = 1)
                        }
                        innerTextField()
                    }
                    if (focused && value.isNotEmpty()) {
                        val clear = stringResource(R.string.text_field_clear)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(HitTarget.minimum)
                                .fcClickable(
                                    onClick = {
                                        onValueChange("")
                                        focusRequester.requestFocus()
                                    },
                                    shape = CircleShape,
                                    pressedScale = 0.9f,
                                )
                                .semantics { contentDescription = clear },
                        ) {
                            Icon(GiftIcons.Clear, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.callout.glyphSize(1.2f)))
                        }
                    }
                }
            }
        },
    )
}

@GiftPreviews
@Composable
private fun FCTextFieldPreview() = PreviewFrame {
    var url by remember { mutableStateOf("https://www.galaxus.ch/fr/s1/product/lego-technic") }
    var name by remember { mutableStateOf("") }
    FCTextField("Lien du cadeau", url, { url = it }, leadingIcon = GiftIcons.Search, placeholder = "Coller un lien", isLabelHidden = true)
    FCTextField("Prénom", name, { name = it }, placeholder = "Ex. Léo")
}
