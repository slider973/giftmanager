package ch.jonathanlemaine.giftmanager.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

private val countryOptions = Countries.all.map { PickerOption(it.code, "${it.flag} ${it.name}") }
private val currencyOptions = Countries.currencies.map { PickerOption(it, it) }

/**
 * Prénom, pays et devise (iOS : `ProfileFields`), partagés entre la configuration et l'écran Profil.
 *
 * Prénom : [FCTextField] (majuscule initiale, saisie automatique « prénom »). Pays et devise : une carte à
 * deux lignes de choix ([PickerRow]). La devise suit le pays côté ViewModel, uniquement quand on le change.
 */
@Composable
fun ProfileFields(
    name: String,
    country: String,
    currency: String,
    onNameChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m), modifier = modifier.fillMaxWidth()) {
        FCTextField(
            label = stringResource(R.string.profile_field_name),
            value = name,
            onValueChange = onNameChange,
            leadingIcon = GiftIcons.Person,
            placeholder = stringResource(R.string.profile_field_name_prompt),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.semantics { contentType = ContentType.PersonFirstName },
        )
        Column(modifier = Modifier.fillMaxWidth().fcCard(padding = 0.dp)) {
            PickerRow(
                icon = GiftIcons.Globe,
                label = stringResource(R.string.profile_field_country),
                selectedText = countryOptions.firstOrNull { it.value == country }?.text ?: Countries.name(country),
                options = countryOptions,
                selectedValue = country,
                onSelect = onCountryChange,
            )
            SettingsDivider()
            PickerRow(
                icon = GiftIcons.Money,
                label = stringResource(R.string.profile_field_currency),
                selectedText = currency,
                options = currencyOptions,
                selectedValue = currency,
                onSelect = onCurrencyChange,
            )
        }
    }
}

@GiftPreviews
@Composable
private fun ProfileFieldsPreview() = PreviewFrame {
    var name by remember { mutableStateOf("Jonathan") }
    ProfileFields(name, "CH", "CHF", { name = it }, {}, {})
}
