package ch.jonathanlemaine.giftmanager.ui.gifts

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewParser
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.PickerOption
import ch.jonathanlemaine.giftmanager.ui.common.PickerRow
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.SettingsDivider
import ch.jonathanlemaine.giftmanager.ui.common.ToggleRow
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.common.TopBarTextAction
import ch.jonathanlemaine.giftmanager.ui.components.CountryFlag
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.StoreLinkRow
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Rappels du formulaire d'ajout ou de modification d'un cadeau. */
data class AddGiftActions(
    val onClose: () -> Unit,
    val onUrlChange: (String) -> Unit,
    val onUrlSubmit: () -> Unit,
    val onTitleChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onPriceChange: (String) -> Unit,
    val onFavoriteChange: (Boolean) -> Unit,
    val onSubmitToParentsChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onConfirmDuplicate: () -> Unit,
    val onDismissDuplicate: () -> Unit,
    val onDismissError: () -> Unit,
    val onCurrencyChange: (String) -> Unit = {},
    /** `null` : « Toute l'année ». */
    val onEventChange: (UUID?) -> Unit = {},
    /** Photo choisie dans le sélecteur système : l'hôte la redimensionne puis appelle `onImagePicked`. */
    val onPhotoPicked: (Uri) -> Unit = {},
    val onAddLink: (DraftLink) -> Unit = {},
    val onRemoveLink: (UUID) -> Unit = {},
)

/**
 * Ajouter / modifier un cadeau (écran 4 de la maquette, iOS : `AddGiftView`).
 *
 * Champ de lien + « Coller », carte d'aperçu (photo ou image de la boutique, « Recherche de l'aperçu… »,
 * bouton « Photo » — sélecteur système, sans autorisation —, nom, boutique avec drapeau, prix + devise),
 * options (soumettre aux parents et qui verra l'idée, « Très envie », événement « Pour », notes), « Autres
 * liens », et le bouton d'enregistrement fixé en bas, au-dessus du clavier. Un doublon (#61) demande
 * confirmation. La barre d'onglets est masquée sur cette route (formulaire plein écran, ✕ pour sortir).
 */
@Composable
fun AddGiftScreen(state: AddGiftUiState, actions: AddGiftActions, modifier: Modifier = Modifier) {
    val title = stringResource(
        when {
            state.isEditing -> R.string.add_gift_title_edit
            state.kind == WishKind.IDEA -> R.string.add_gift_title_idea
            else -> R.string.add_gift_title_new
        },
    )
    val scrollState = rememberScrollState()
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }
    var addingLink by rememberSaveable { mutableStateOf(false) }

    TopBarScreen(
        modifier = modifier,
        topBar = { GiftTopBar(TopBarNavigation.CLOSE, onNavigate = actions.onClose, title = title, showDivider = scrolled) },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (state.isLoadingExisting) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GiftLoadingView(size = 72.dp, label = stringResource(R.string.add_gift_loading))
                    }
                } else {
                    // Le bas (barre de navigation, clavier) est porté par la barre du bouton, pas par la colonne.
                    ScreenColumn(
                        scrollState = scrollState,
                        spacing = GiftTheme.spacing.l,
                        modifier = Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                    ) {
                        UrlField(state, actions)
                        PreviewCard(state, actions)
                        if (!state.isEditing || state.kind == WishKind.WISH) OptionsCard(state, actions)
                        OtherLinks(state, actions, onAddLink = { addingLink = true })
                    }
                }
            }
            SaveBar(state, actions)
        }
    }

    if (addingLink) {
        AddLinkSheet(defaultCurrency = state.currency, onAdd = actions.onAddLink, onDismiss = { addingLink = false })
    }
    state.duplicate?.let { duplicate ->
        val childName = state.child?.firstName
        val eventTitle = duplicate.eventId?.let { id -> state.selectableEvents.firstOrNull { it.id == id }?.title }
        val message = when {
            childName == null -> stringResource(R.string.add_gift_duplicate_message, duplicate.title)
            duplicate.eventId == null -> stringResource(R.string.add_gift_duplicate_message_no_event, duplicate.title, childName)
            eventTitle != null -> stringResource(R.string.add_gift_duplicate_message_event, duplicate.title, eventTitle, childName)
            else -> stringResource(R.string.add_gift_duplicate_message_child, duplicate.title, childName)
        }
        GiftAlertDialog(
            title = stringResource(R.string.add_gift_duplicate_title),
            message = message,
            confirmLabel = stringResource(R.string.add_gift_duplicate_confirm),
            onConfirm = actions.onConfirmDuplicate,
            onDismiss = actions.onDismissDuplicate,
            dismissLabel = stringResource(R.string.cancel),
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** Champ de lien (libellé masqué, lu par TalkBack) + bouton « Coller » ; indice tant qu'il est vide. */
@Composable
private fun UrlField(state: AddGiftUiState, actions: AddGiftActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pasteLabel = stringResource(R.string.add_gift_paste)
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
            FCTextField(
                label = stringResource(R.string.add_gift_url),
                value = state.urlText,
                onValueChange = actions.onUrlChange,
                leadingIcon = GiftIcons.Search,
                placeholder = stringResource(R.string.add_gift_url_placeholder),
                isLabelHidden = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { actions.onUrlSubmit() }),
                modifier = Modifier.weight(1f),
            )
            // Collage explicite (iOS : PasteButton) : le lien collé lance l'aperçu tout de suite.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HitTarget.minimum)
                    .fcClickable(
                        onClick = {
                            scope.launch {
                                val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                if (!text.isNullOrBlank()) {
                                    actions.onUrlChange(text.trim())
                                    actions.onUrlSubmit()
                                }
                            }
                        },
                        shape = CapsuleShape,
                        pressedScale = 0.9f,
                    )
                    .background(colors.primary, CapsuleShape)
                    .semantics { contentDescription = pasteLabel },
            ) {
                Icon(GiftIcons.Paste, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
        if (state.urlText.isEmpty() && !state.isEditing) {
            Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(GiftIcons.Sparkles, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.caption.glyphSize()))
                Text(stringResource(R.string.add_gift_url_hint), style = type.caption, color = colors.textSecondary)
            }
        }
    }
}

/**
 * Carte d'aperçu : image (photo choisie, sinon celle de la boutique) dans un cadre bordé de 200 dp, pastille
 * « Recherche de l'aperçu… » en haut, bouton « Photo » en bas ; puis nom, boutique et prix sous des libellés
 * visibles. Aux grandes polices, boutique et prix s'empilent.
 */
@Composable
private fun PreviewCard(state: AddGiftUiState, actions: AddGiftActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val stacked = LocalDensity.current.fontScale >= 1.5f
    Column(modifier = Modifier.fillMaxWidth().fcCard()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(GiftTheme.radius.thumbShape)
                .background(colors.surface)
                .border(1.dp, colors.separator, GiftTheme.radius.thumbShape),
        ) {
            val picked = rememberDecodedImage(state.imageBytes)
            if (picked != null) {
                Image(
                    bitmap = picked,
                    contentDescription = stringResource(R.string.add_gift_photo_picked),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RemoteImage(
                    url = state.imageUrl,
                    contentScale = ContentScale.Fit,
                    placeholderSeed = state.title.ifBlank { null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            FetchingChip(visible = state.isFetching, modifier = Modifier.align(Alignment.TopStart).padding(spacing.s))
            PhotoButton(onPicked = actions.onPhotoPicked, modifier = Modifier.align(Alignment.BottomEnd).padding(end = spacing.xs))
        }
        if (state.previewFailed) {
            FCNotice(
                GiftIcons.Warning,
                stringResource(R.string.add_gift_preview_failed),
                tone = FCNoticeTone.WARNING,
                modifier = Modifier.padding(top = spacing.m).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FCTextField(
            label = stringResource(R.string.add_gift_name),
            value = state.title,
            onValueChange = actions.onTitleChange,
            placeholder = stringResource(R.string.add_gift_name_placeholder),
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.padding(top = spacing.l),
        )
        HorizontalDivider(thickness = 1.dp, color = colors.separator, modifier = Modifier.padding(vertical = spacing.m))
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
                StoreField(state.store)
                PriceBlock(state, actions, alignEnd = false)
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                StoreField(state.store, Modifier.weight(1f))
                PriceBlock(state, actions, alignEnd = true)
            }
        }
    }
}

/** Décode la photo choisie (JPEG déjà redimensionné) hors du fil principal. */
@Composable
private fun rememberDecodedImage(bytes: ByteArray?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, bytes) {
        value = bytes?.let { data ->
            withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
        }
    }
    return image
}

/** Pastille « Recherche de l'aperçu… » (fondu de 200 ms, coupé si animations réduites), annoncée par TalkBack. */
@Composable
private fun FetchingChip(visible: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = GiftTheme.reduceMotion
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(if (reduceMotion) snap() else tween(200)),
        exit = fadeOut(if (reduceMotion) snap() else tween(150)),
        modifier = modifier,
    ) { FetchingChipContent() }
}

@Composable
private fun FetchingChipContent() {
    val colors = GiftTheme.colors
    val text = stringResource(R.string.add_gift_fetching)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .fcCardChip()
            .padding(horizontal = GiftTheme.spacing.m)
            .clearAndSetSemantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        Text(text, style = GiftTheme.type.captionBold, color = colors.textPrimary)
    }
}

/** Fond des pastilles posées sur l'image : capsule `surface` à l'ombre unique des cartes. */
@Composable
private fun Modifier.fcCardChip(): Modifier = this
    .dropShadow(CapsuleShape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp)))
    .background(GiftTheme.colors.surface, CapsuleShape)

/**
 * Bouton « Photo » (iOS : `PhotosPicker`) : sélecteur de photos du système, images seulement, **sans
 * autorisation**. Cible de 48 dp autour d'une pastille de 32 dp.
 */
@Composable
private fun PhotoButton(onPicked: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val label = stringResource(R.string.add_gift_photo_a11y)
    // En aperçu, aucun registre d'activité : le bouton s'affiche sans rien lancer.
    val launcher = if (LocalInspectionMode.current) {
        null
    } else {
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    }
    val launch: () -> Unit = { launcher?.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = HitTarget.minimum)
            .fcClickable(onClick = launch, shape = CapsuleShape)
            .semantics { contentDescription = label },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
            modifier = Modifier.heightIn(min = 32.dp).fcCardChip().padding(horizontal = GiftTheme.spacing.m),
        ) {
            Icon(GiftIcons.Camera, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(GiftTheme.type.captionBold.glyphSize(1.3f)))
            Text(stringResource(R.string.add_gift_photo), style = GiftTheme.type.captionBold, color = colors.textPrimary)
        }
    }
}

/** « Boutique » : drapeau + nom détecté depuis le lien, sinon « Selon le lien ». Un seul nœud TalkBack. */
@Composable
private fun StoreField(store: StoreCatalog.Store?, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), modifier = modifier.semantics(mergeDescendants = true) { }) {
        FieldLabel(stringResource(R.string.add_gift_store))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs + 2.dp),
            modifier = Modifier.heightIn(min = HitTarget.minimum),
        ) {
            if (store != null && store.name.isNotEmpty()) {
                store.country?.let { CountryFlag(it, style = type.body) }
                Text(store.name, style = type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(stringResource(R.string.add_gift_store_auto), style = type.body, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun PriceBlock(state: AddGiftUiState, actions: AddGiftActions, alignEnd: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        FieldLabel(stringResource(R.string.add_gift_price), Modifier.clearAndSetSemantics { })
        PriceField(state.priceText, actions.onPriceChange, state.currency, actions.onCurrencyChange)
    }
}

/**
 * Prix + devise dans un même champ bordé (iOS : `PriceField`) : chiffres tabulaires calés à droite, filet,
 * devise en menu `primary`. Fond `background` pour se détacher de la carte.
 */
@Composable
private fun PriceField(price: String, onPriceChange: (String) -> Unit, currency: String, onCurrencyChange: (String) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val shape = GiftTheme.radius.fieldShape
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val label = stringResource(R.string.add_gift_price)
    val textStyle = type.callout.tabular().copy(fontWeight = FontWeight.SemiBold, color = colors.textPrimary, textAlign = TextAlign.End)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        modifier = Modifier
            .heightIn(min = HitTarget.minimum)
            .background(colors.background, shape)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) colors.primary else colors.separator, shape)
            .padding(start = GiftTheme.spacing.m),
    ) {
        BasicTextField(
            value = price,
            onValueChange = onPriceChange,
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            interactionSource = source,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.widthIn(min = 56.dp, max = 96.dp).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (price.isEmpty()) Text(stringResource(R.string.add_gift_price_placeholder), style = textStyle, color = colors.textSecondary)
                    inner()
                }
            },
        )
        Box(Modifier.width(1.dp).height(20.dp).background(colors.separator))
        CurrencyMenu(currency, onCurrencyChange)
    }
}

/** Devise en menu : « CHF ▾ » `primary` ; l'option courante cochée. TalkBack : « Devise, CHF, liste déroulante ». */
@Composable
private fun CurrencyMenu(currency: String, onCurrencyChange: (String) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.add_gift_currency)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = HitTarget.minimum)
                .fcClickable(onClick = { open = true }, role = Role.DropdownList, shape = GiftTheme.radius.fieldShape, pressedScale = 1f)
                .padding(start = GiftTheme.spacing.xs, end = GiftTheme.spacing.xs)
                .semantics {
                    contentDescription = label
                    stateDescription = currency
                },
        ) {
            Text(currency, style = type.callout.copy(fontWeight = FontWeight.Medium), color = colors.primary)
            Icon(GiftIcons.DropDown, contentDescription = null, tint = colors.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = GiftTheme.radius.fieldShape, containerColor = colors.surface) {
            Countries.currencies.forEach { code ->
                val isSelected = code == currency
                DropdownMenuItem(
                    text = {
                        Text(code, style = if (isSelected) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout, color = colors.textPrimary)
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(GiftIcons.Checkmark, contentDescription = null, tint = colors.secondary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onCurrencyChange(code)
                    },
                    modifier = Modifier.heightIn(min = HitTarget.minimum).semantics { selected = isSelected },
                )
            }
        }
    }
}

/**
 * Options (iOS : carte d'options) : « Soumettre aux parents » + qui verra l'idée (#57, en `takenFg` si personne),
 * « Très envie », « Pour » (événement), puis les notes. Lignes de réglage à la manière du Profil.
 */
@Composable
private fun OptionsCard(state: AddGiftUiState, actions: AddGiftActions) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    Column(modifier = Modifier.fillMaxWidth().fcCard(padding = 0.dp)) {
        var needsDivider = false
        if (state.showsSubmitToParents) {
            ToggleRow(
                icon = GiftIcons.Verified,
                label = stringResource(R.string.add_gift_submit_to_parents),
                checked = state.submitToParents,
                onCheckedChange = actions.onSubmitToParentsChange,
                iconTint = colors.primary,
            )
            Text(
                text = audienceText(state),
                style = GiftTheme.type.caption,
                color = if (state.audienceIsEmpty) colors.takenFg else colors.textSecondary,
                modifier = Modifier
                    .padding(start = spacing.l, end = spacing.l, bottom = spacing.m)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            needsDivider = true
        }
        if (state.showsFavorite) {
            if (needsDivider) SettingsDivider()
            ToggleRow(
                icon = GiftIcons.Heart,
                label = stringResource(R.string.add_gift_favorite),
                checked = state.isFavorite,
                onCheckedChange = actions.onFavoriteChange,
                iconTint = colors.heart,
            )
            needsDivider = true
        }
        if (state.showsEventPicker) {
            if (needsDivider) SettingsDivider()
            val any = stringResource(R.string.add_gift_event_any)
            val options = listOf(PickerOption("", any)) + state.selectableEvents.map { PickerOption(it.id.toString(), it.title) }
            val selected = state.eventId?.toString().orEmpty()
            PickerRow(
                icon = GiftIcons.Calendar,
                label = stringResource(R.string.add_gift_event),
                selectedText = options.firstOrNull { it.value == selected }?.text ?: any,
                options = options,
                selectedValue = selected,
                onSelect = { value -> actions.onEventChange(value.takeIf { it.isNotEmpty() }?.let(UUID::fromString)) },
            )
            needsDivider = true
        }
        if (needsDivider) SettingsDivider(inset = false)
        FCTextField(
            label = stringResource(R.string.add_gift_notes),
            value = state.notes,
            onValueChange = actions.onNotesChange,
            placeholder = stringResource(R.string.add_gift_notes_placeholder),
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.padding(spacing.l),
        )
    }
}

/** Qui verra l'idée (#57) : les parents décident, ou combien de membres la verront sans eux. */
@Composable
private fun audienceText(state: AddGiftUiState): String {
    if (state.submitToParents) {
        val names = state.parentNames
        return when {
            names.isEmpty() -> stringResource(R.string.add_gift_audience_parents_generic)
            names.size == 1 -> stringResource(R.string.add_gift_audience_parent_one, names.first())
            else -> stringResource(R.string.add_gift_audience_parent_many, joinedNames(names))
        }
    }
    return when (val count = state.audience) {
        null -> stringResource(R.string.add_gift_audience_unknown)
        0 -> stringResource(R.string.add_gift_audience_none)
        else -> pluralStringResource(R.plurals.add_gift_audience_count, count, count)
    }
}

/** « Autres liens pour ce cadeau » : liens ajoutés (ouvrables, retirables) et « Ajouter un lien ». */
@Composable
private fun OtherLinks(state: AddGiftUiState, actions: AddGiftActions, onAddLink: () -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val context = LocalContext.current
    val fallback = stringResource(R.string.gift_detail_link_fallback)
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s), modifier = Modifier.padding(top = GiftTheme.spacing.s)) {
        Text(
            text = stringResource(R.string.add_gift_other_links),
            style = type.headline,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.add_gift_other_links_hint), style = type.caption, color = colors.textSecondary)
        state.otherLinks.forEach { link ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreLinkRow(
                    store = link.store ?: fallback,
                    countryCode = link.country,
                    priceText = Money.format(link.price, link.currency),
                    onClick = { openInBrowser(context, link.url) },
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    icon = GiftIcons.RemoveCircle,
                    label = stringResource(R.string.add_gift_remove_link),
                    onClick = { actions.onRemoveLink(link.id) },
                    tint = colors.takenFg,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
            modifier = Modifier
                .heightIn(min = HitTarget.minimum)
                .fcClickable(onClick = onAddLink, shape = GiftTheme.radius.fieldShape, pressedScale = 1f)
                .padding(end = GiftTheme.spacing.s),
        ) {
            Icon(GiftIcons.AddCircle, contentDescription = null, tint = colors.primary)
            Text(stringResource(R.string.add_gift_add_link), style = type.callout.copy(fontWeight = FontWeight.Medium), color = colors.primary)
        }
    }
}

/**
 * Bouton d'enregistrement toujours visible (maquette : sous l'aperçu) : barre `background` à filet supérieur,
 * au-dessus de la barre de navigation et du clavier.
 */
@Composable
private fun SaveBar(state: AddGiftUiState, actions: AddGiftActions) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.separator)
        PrimaryButton(
            text = stringResource(
                when {
                    state.isEditing -> R.string.add_gift_save_edit
                    state.kind == WishKind.IDEA -> R.string.add_gift_save_idea
                    else -> R.string.add_gift_save_new
                },
            ),
            onClick = actions.onSave,
            leadingIcon = GiftIcons.Checkmark,
            isLoading = state.isSaving,
            enabled = state.canSave,
            modifier = Modifier
                .widthIn(max = ScreenContentMaxWidth)
                .padding(start = spacing.xl, end = spacing.xl, top = spacing.m, bottom = spacing.s),
        )
    }
}

/**
 * Feuille « Autre lien » (iOS : `AddLinkSheet`) : lien, boutique détectée (drapeau), prix + devise (celle du
 * pays de la boutique dès qu'elle est reconnue). « Ajouter ce lien » n'est actif qu'avec un lien valide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLinkSheet(defaultCurrency: String, onAdd: (DraftLink) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        AddLinkContent(defaultCurrency, onAdd, onDismiss)
    }
}

@Composable
private fun AddLinkContent(defaultCurrency: String, onAdd: (DraftLink) -> Unit, onDismiss: () -> Unit) {
    val spacing = GiftTheme.spacing
    var url by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(defaultCurrency) }
    val normalized = LinkPreviewParser.normalizedUrl(url)
    val store = normalized?.let(StoreCatalog::store)
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.xl, end = spacing.xl, bottom = spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.add_link_title),
                style = GiftTheme.type.title,
                color = GiftTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TopBarTextAction(stringResource(R.string.cancel), onClick = onDismiss)
        }
        FCTextField(
            label = stringResource(R.string.add_link_url),
            value = url,
            onValueChange = { text ->
                url = text
                LinkPreviewParser.normalizedUrl(text)?.let(StoreCatalog::store)?.currency?.let { currency = it }
            },
            leadingIcon = GiftIcons.Link,
            placeholder = stringResource(R.string.add_link_url_placeholder),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            modifier = Modifier.fillMaxWidth().fcCard(),
        ) {
            StoreField(store, Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), horizontalAlignment = Alignment.End) {
                FieldLabel(stringResource(R.string.add_gift_price), Modifier.clearAndSetSemantics { })
                PriceField(price, { price = it }, currency, { currency = it })
            }
        }
        PrimaryButton(
            text = stringResource(R.string.add_link_submit),
            onClick = {
                val link = normalized ?: return@PrimaryButton
                onAdd(DraftLink(url = link, store = store?.name, country = store?.country, price = LinkPreviewParser.parsePrice(price), currency = currency))
                onDismiss()
            },
            leadingIcon = GiftIcons.Add,
            enabled = normalized != null,
        )
    }
}

// region Aperçus

private val previewActions = AddGiftActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

private val previewWish = AddGiftUiState(
    kind = WishKind.WISH,
    owned = false,
    isEditing = false,
    child = GiftPreviewData.leo,
    selectableEvents = listOf(GiftPreviewData.christmas, GiftPreviewData.birthday),
    urlText = "https://www.galaxus.ch/fr/s1/product/lego-technic-mclaren-f1",
    title = "LEGO Technic McLaren F1",
    priceText = "199.00",
    currency = "CHF",
    store = StoreCatalog.Store("Galaxus", "CH", "CHF"),
    isFavorite = true,
    eventId = GiftPreviewData.christmas.id,
    otherLinks = listOf(DraftLink(url = "https://www.amazon.fr/dp/1", store = "Amazon", country = "FR", price = java.math.BigDecimal("189.99"), currency = "EUR")),
)

@GiftPreviews
@Composable
private fun AddGiftWishPreview() = ScreenPreviewFrame(height = 1300.dp) { AddGiftScreen(previewWish, previewActions) }

@GiftPreviews
@Composable
private fun AddGiftIdeaPreview() = ScreenPreviewFrame(height = 1300.dp) {
    AddGiftScreen(
        AddGiftUiState(
            kind = WishKind.IDEA,
            owned = false,
            isEditing = false,
            child = GiftPreviewData.zoe,
            parentNames = listOf("Claire", "Paul"),
            urlText = "https://www.manor.ch/fr/p/123",
            isFetching = true,
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun AddGiftFailedPreview() = ScreenPreviewFrame(height = 1300.dp) {
    AddGiftScreen(
        AddGiftUiState(
            kind = WishKind.IDEA,
            owned = false,
            isEditing = false,
            child = GiftPreviewData.zoe,
            urlText = "https://boutique.example/jouet",
            previewFailed = true,
            submitToParents = false,
            audience = 0,
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun AddLinkPreview() = GiftManagerTheme {
    Column(Modifier.background(GiftTheme.colors.background).padding(top = GiftTheme.spacing.l)) {
        AddLinkContent(defaultCurrency = "EUR", onAdd = {}, onDismiss = {})
    }
}

// endregion
