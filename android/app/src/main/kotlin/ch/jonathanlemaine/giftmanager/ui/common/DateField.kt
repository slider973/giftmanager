package ch.jonathanlemaine.giftmanager.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Le sélecteur parle français (mois, jours, format de saisie), comme le `DatePicker` iOS en `fr_FR`. */
private val PickerLocale: Locale = Locale.FRANCE

/**
 * Champ date (iOS : `DatePicker` compact). Même habit que `FCTextField` — libellé `captionBold` au-dessus,
 * champ `surface` bordé `separator` — avec l'icône calendrier, la date longue (« 12 mars 2019 ») et ▾ en
 * `primary`. Tout le champ est un bouton (TalkBack : « Née / né le, 12 mars 2019, bouton, appuyez deux fois
 * pour choisir une date ») qui ouvre le [DatePickerDialog] Material 3 aux couleurs de l'app.
 *
 * [max] borne la date (naissance : aujourd'hui) : jours et années au-delà grisés et non sélectionnables.
 * Le sélecteur s'ouvre en saisie clavier quand le calendrier ne tiendrait pas (paysage, police ≥ 150 %).
 */
@Composable
fun DateField(label: String, date: LocalDate, onDateChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, max: LocalDate? = null) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val shape = GiftTheme.radius.fieldShape
    var open by rememberSaveable { mutableStateOf(false) }
    val dateText = Formatting.dateText(DayDate(date))
    val description = "$label, $dateText"
    val actionLabel = stringResource(R.string.date_field_choose)

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s), modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = type.captionBold, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HitTarget.minimum.scaledByFont())
                .fcClickable(onClick = { open = true }, onClickLabel = actionLabel, shape = shape, pressedScale = 1f)
                .background(colors.surface, shape)
                .border(1.dp, colors.separator, shape)
                .padding(horizontal = spacing.l, vertical = spacing.m)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Icon(GiftIcons.Calendar, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.callout.glyphSize(1.25f)))
            Text(text = dateText, style = type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(GiftIcons.DropDown, contentDescription = null, tint = colors.primary, modifier = Modifier.size(type.callout.glyphSize(1.4f)))
        }
    }

    if (open) {
        GiftDatePickerDialog(
            date = date,
            max = max,
            onConfirm = {
                onDateChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiftDatePickerDialog(date: LocalDate, max: LocalDate?, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = GiftTheme.colors
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Le calendrier fait ~570 dp de haut : en paysage ou aux grandes polices, la saisie clavier tient.
    val compact = windowHeight < 600.dp || density.fontScale >= 1.5f
    // Configuration française pour le seul sélecteur : noms des mois et des jours, format « jj/mm/aaaa ».
    val frenchConfiguration = remember(configuration) {
        Configuration(configuration).apply { setLocale(PickerLocale) }
    }
    CompositionLocalProvider(LocalConfiguration provides frenchConfiguration) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
            yearRange = DatePickerDefaults.YearRange.first..(max?.year ?: DatePickerDefaults.YearRange.last),
            initialDisplayMode = if (compact) DisplayMode.Input else DisplayMode.Picker,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = max == null || !utcTimeMillis.toLocalDate().isAfter(max)

                override fun isSelectableYear(year: Int): Boolean = max == null || year <= max.year
            },
        )
        val pickerColors = giftDatePickerColors()
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                DialogTextButton(
                    text = stringResource(R.string.date_picker_ok),
                    enabled = state.selectedDateMillis != null,
                    emphasized = true,
                    onClick = { state.selectedDateMillis?.let { onConfirm(it.toLocalDate()) } },
                )
            },
            dismissButton = { DialogTextButton(text = stringResource(R.string.cancel), onClick = onDismiss) },
            shape = GiftTheme.radius.cardShape,
            colors = pickerColors,
        ) {
            DatePicker(state = state, colors = pickerColors)
        }
    }
}

/** Bouton texte des dialogues : `primary`, ≥ 48 dp (même habit que `GiftAlertDialog`). */
@Composable
private fun DialogTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true, emphasized: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = HitTarget.minimum)) {
        Text(
            text = text,
            style = GiftTheme.type.callout.copy(fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium),
            color = if (enabled) GiftTheme.colors.primary else GiftTheme.colors.textSecondary,
        )
    }
}

/**
 * Couleurs du sélecteur : carte `surface`, jour choisi en disque `primary` / `onPrimary`, aujourd'hui cerclé
 * `primary`, jours hors bornes en `textSecondary` atténué (rôle M3 « disabled »).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun giftDatePickerColors(): DatePickerColors {
    val c = GiftTheme.colors
    return DatePickerDefaults.colors(
        containerColor = c.surface,
        titleContentColor = c.textSecondary,
        headlineContentColor = c.textPrimary,
        weekdayContentColor = c.textSecondary,
        subheadContentColor = c.textSecondary,
        navigationContentColor = c.textPrimary,
        yearContentColor = c.textPrimary,
        currentYearContentColor = c.primary,
        selectedYearContentColor = c.onPrimary,
        selectedYearContainerColor = c.primary,
        dayContentColor = c.textPrimary,
        selectedDayContentColor = c.onPrimary,
        selectedDayContainerColor = c.primary,
        todayContentColor = c.primary,
        todayDateBorderColor = c.primary,
        dividerColor = c.separator,
    )
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@GiftPreviews
@Composable
private fun DateFieldPreview() = PreviewFrame {
    DateField(label = "Née / né le", date = LocalDate.of(2019, 3, 12), onDateChange = {}, max = LocalDate.of(2026, 11, 1))
    DateField(label = "Date", date = LocalDate.of(2026, 12, 25), onDateChange = {})
}

@OptIn(ExperimentalMaterial3Api::class)
@GiftPreviews
@Composable
private fun DatePickerDialogPreview() = PreviewFrame {
    GiftDatePickerDialog(date = LocalDate.of(2019, 3, 12), max = LocalDate.of(2026, 11, 1), onConfirm = {}, onDismiss = {})
}
