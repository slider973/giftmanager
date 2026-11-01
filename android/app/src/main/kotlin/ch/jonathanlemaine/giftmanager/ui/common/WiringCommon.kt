package ch.jonathanlemaine.giftmanager.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

// Briques de câblage minimales partagées par les écrans de l'onboarding, de la famille et du profil.
// Le `ui-designer` les remplace par les écrans définitifs ; les signatures des écrans, elles, restent.

/** Colonne défilante sur le fond de l'app, avec les marges système (bord à bord). */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.l),
        modifier = modifier
            .fcScreenBackground()
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(GiftTheme.spacing.xl),
        content = content,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(text = text, style = GiftTheme.type.largeTitle, color = GiftTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
}

@Composable
fun BodyText(text: String) {
    Text(text = text, style = GiftTheme.type.body, color = GiftTheme.colors.textSecondary)
}

/** Alerte d'erreur « Oups » + message de [error]. Rien n'est affiché si [error] est `null`. */
@Composable
fun GiftErrorDialog(error: GiftError?, onDismiss: () -> Unit) {
    if (error == null) return
    val colors = GiftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.error_dialog_ok), color = colors.primary) } },
        title = { Text(stringResource(R.string.error_dialog_title), style = GiftTheme.type.title, color = colors.textPrimary) },
        text = { Text(stringResource(error.messageRes), style = GiftTheme.type.callout, color = colors.textSecondary) },
        containerColor = colors.surface,
    )
}

/** Prénom, pays et devise (iOS : `ProfileFields`), partagés entre la configuration et l'écran Profil. */
@Composable
fun ProfileFields(
    name: String,
    country: String,
    currency: String,
    onNameChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    FCTextField(
        label = stringResource(R.string.profile_field_name),
        value = name,
        onValueChange = onNameChange,
        placeholder = stringResource(R.string.profile_field_name_prompt),
    )
    ChoiceRow(
        label = stringResource(R.string.profile_field_country),
        selected = Countries.all.firstOrNull { it.code == country }?.let { "${it.flag} ${it.name}" } ?: country,
        options = Countries.all.map { it.code to "${it.flag} ${it.name}" },
        onSelect = onCountryChange,
    )
    ChoiceRow(
        label = stringResource(R.string.profile_field_currency),
        selected = currency,
        options = Countries.currencies.map { it to it },
        onSelect = onCurrencyChange,
    )
}

@Composable
private fun ChoiceRow(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, style = GiftTheme.type.body, color = GiftTheme.colors.textSecondary)
        Column {
            TextLinkButton(text = selected, onClick = { expanded = true })
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, title) ->
                    DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false })
                }
            }
        }
    }
}
