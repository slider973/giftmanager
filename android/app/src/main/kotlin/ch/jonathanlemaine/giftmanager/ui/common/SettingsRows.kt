package ch.jonathanlemaine.giftmanager.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

// Équivalent Compose de la liste groupée d'iOS (`.insetGrouped` + `fcListRow`) : un titre de section, une
// carte blanche dont les lignes sont séparées par des filets, et une note de bas de section.

/** Taille des icônes de ligne : 24 dp, qui grandit un peu avec la police. */
@Composable
private fun rowIconSize() = 24.dp.scaledByFont(1.4f)

/** Section groupée : [title] (titre TalkBack), carte [content], puis [footer] explicatif. */
@Composable
fun SettingsSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s), modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = type.captionBold,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.l).semantics { heading() },
            )
        }
        Column(modifier = Modifier.fillMaxWidth().fcCard(padding = 0.dp), content = content)
        footer?.let {
            Text(text = it, style = type.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = spacing.l))
        }
    }
}

/**
 * Ligne de réglage : icône [icon] alignée (`textSecondary`, iOS : `SettingsLabelStyle`), puis [content].
 * Hauteur ≥ 56 dp ; poser le clic sur [modifier] pour que toute la ligne soit la cible.
 */
@Composable
fun SettingsRow(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = GiftTheme.colors.textSecondary,
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = GiftTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HitTarget.minimum + spacing.s)
            .padding(horizontal = spacing.l, vertical = spacing.xs),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(rowIconSize())) }
        content()
    }
}

/** Ligne-bouton texte (« Se déconnecter », « Supprimer mon compte ») : libellé coloré, toute la ligne cliquable. */
@Composable
fun SettingsActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = GiftTheme.colors.primary,
) {
    SettingsRow(
        icon = icon,
        iconTint = color,
        modifier = modifier.fcClickable(onClick = onClick, shape = GiftTheme.radius.fieldShape, pressedScale = 1f),
    ) {
        Text(
            text = text,
            style = GiftTheme.type.callout.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Filet entre deux lignes, aligné sur le texte (après l'icône). */
@Composable
fun SettingsDivider(modifier: Modifier = Modifier, inset: Boolean = true) {
    val spacing = GiftTheme.spacing
    HorizontalDivider(
        thickness = 1.dp,
        color = GiftTheme.colors.separator,
        modifier = modifier.padding(start = if (inset) spacing.l + rowIconSize() + spacing.m else spacing.l),
    )
}

/**
 * Interrupteur de réglage : toute la ligne bascule (TalkBack : « Rappels d'anniversaire, interrupteur,
 * activé »). Le `Switch` M3 n'est qu'un témoin, aux couleurs de la marque.
 */
@Composable
fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val source = remember { MutableInteractionSource() }
    SettingsRow(
        icon = icon,
        modifier = modifier
            .fcPressable(source, GiftTheme.radius.fieldShape, pressedScale = 1f)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    ) {
        Text(text = label, style = GiftTheme.type.callout, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.background,
                uncheckedBorderColor = colors.textSecondary,
            ),
        )
    }
}

/** Choix d'un menu déroulant : [value] enregistrée, [text] affiché (« 🇨🇭 Suisse »). */
data class PickerOption(val value: String, val text: String)

/**
 * Ligne de choix (iOS : `Picker` en menu) : icône, libellé, valeur choisie en `primary` + ▾. Toucher la
 * ligne ouvre un menu calé sur le bord droit, l'option courante cochée. TalkBack : « Pays, Suisse, liste
 * déroulante, appuyez deux fois pour choisir ». La valeur passe à la ligne aux grandes polices.
 */
@Composable
fun PickerRow(
    icon: ImageVector,
    label: String,
    selectedText: String,
    options: List<PickerOption>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        SettingsRow(
            icon = icon,
            modifier = Modifier.fcClickable(
                onClick = { expanded = true },
                role = Role.DropdownList,
                onClickLabel = stringResource(R.string.picker_choose),
                shape = GiftTheme.radius.fieldShape,
                pressedScale = 1f,
            ),
        ) {
            Text(text = label, style = type.callout, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                text = selectedText,
                style = type.callout.copy(fontWeight = FontWeight.Medium),
                color = colors.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(GiftIcons.DropDown, contentDescription = null, tint = colors.primary, modifier = Modifier.size(rowIconSize()))
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = GiftTheme.spacing.l)) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = GiftTheme.radius.fieldShape,
                containerColor = colors.surface,
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selectedValue
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.text,
                                style = if (isSelected) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout,
                                color = colors.textPrimary,
                            )
                        },
                        onClick = {
                            onSelect(option.value)
                            expanded = false
                        },
                        trailingIcon = if (isSelected) {
                            { Icon(GiftIcons.Checkmark, contentDescription = null, tint = colors.secondary) }
                        } else {
                            null
                        },
                        modifier = Modifier.semantics { selected = isSelected },
                    )
                }
            }
        }
    }
}

@GiftPreviews
@Composable
private fun SettingsRowsPreview() = PreviewFrame {
    SettingsSection(title = "Notifications", footer = "Aucune notification ne dit qui a réservé quoi.") {
        ToggleRow(GiftIcons.Calendar, "Rappels d'achats (J-30, J-15, J-7, J-2)", checked = true, onCheckedChange = {})
        SettingsDivider()
        ToggleRow(GiftIcons.Cake, "Rappels d'anniversaire (J-30, J-7)", checked = false, onCheckedChange = {})
    }
    SettingsSection(title = null) {
        PickerRow(GiftIcons.Money, "Devise", "CHF", listOf(PickerOption("CHF", "CHF")), "CHF", {})
        SettingsDivider()
        SettingsActionRow("Se déconnecter", onClick = {})
    }
}
