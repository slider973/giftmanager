package ch.jonathanlemaine.giftmanager.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.gifts.SearchFilters
import ch.jonathanlemaine.giftmanager.core.gifts.SearchGroup
import ch.jonathanlemaine.giftmanager.core.gifts.SearchResult
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftCard
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.GiftStatus
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import java.math.BigDecimal

/** Rappels de la recherche. */
data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onToggleOnlyAvailable: () -> Unit,
    val onSelectCountry: (String?) -> Unit,
    val onOpenItem: (WishItem) -> Unit,
    val onRefresh: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Recherche (iOS : `SearchView`) : grand titre, champ « Cadeau, enfant… », puces de filtre défilantes (« Disponibles »,
 * « Tous pays », un pays par boutique), puis les résultats regroupés par enfant sous des en-têtes qui restent collés
 * en haut pendant le défilement. Le badge d'une carte est absent pour la liste d'un parent (mode surprise). Tirer pour
 * actualiser. Les résultats se recalculent à la frappe, sans requête.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(state: SearchUiState, actions: SearchActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val colors = GiftTheme.colors
    val refreshState = rememberPullToRefreshState()
    val keyboard = LocalSoftwareKeyboardController.current
    // Après le `Scaffold` des onglets, il ne reste en bas que le clavier.
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    // Les puces et les en-têtes collés vont d'un bord à l'autre ; le reste garde la marge d'écran.
    val column = Modifier.widthIn(max = ScreenContentMaxWidth).fillMaxWidth().padding(horizontal = spacing.xl)

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        state = refreshState,
        contentAlignment = Alignment.TopCenter,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = state.isRefreshing,
                containerColor = colors.surface,
                color = colors.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            contentPadding = PaddingValues(top = spacing.l, bottom = spacing.xxl + bottomInset),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") { ScreenTitle(stringResource(R.string.tab_search), modifier = column) }
            item(key = "field") {
                FCTextField(
                    label = stringResource(R.string.search_field_label),
                    value = state.filters.query,
                    onValueChange = actions.onQueryChange,
                    leadingIcon = GiftIcons.Search,
                    isLabelHidden = true,
                    placeholder = stringResource(R.string.search_field_label),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = column,
                )
            }
            item(key = "filters") { FilterChips(state.filters, state.countries, actions) }
            when {
                !state.hasLoaded -> item(key = "loading") {
                    GiftLoadingView(size = 72.dp, label = stringResource(R.string.search_loading), modifier = column.padding(vertical = spacing.xxl))
                }
                state.isEmpty -> item(key = "empty") {
                    EmptyStateView(
                        illustration = Illustration.MASCOT_THINKING,
                        title = stringResource(R.string.search_empty_title),
                        message = stringResource(if (state.hasQuery) R.string.search_empty_query else R.string.search_empty_filters),
                        modifier = column,
                    )
                }
            }
            state.groups.forEach { group ->
                stickyHeader(key = "header-${group.child.id}") { ChildHeader(group.child, group.results.size) }
                items(group.results, key = { "result-${group.child.id}-${it.item.id}" }) { result ->
                    GiftCard(
                        title = result.item.title,
                        imageUrl = result.item.imageUrl,
                        priceText = result.link?.priceText,
                        storeText = result.link?.store,
                        countryCode = result.link?.country,
                        isFavorite = result.item.isFavorite,
                        status = result.badge,
                        onClick = { actions.onOpenItem(result.item) },
                        modifier = column,
                    )
                }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/**
 * En-tête collé d'un enfant : avatar 28 dp, prénom, nombre de résultats. Fond opaque `background` (les cartes
 * glissent dessous), titre TalkBack « Léa, 3 cadeaux ».
 */
@Composable
private fun ChildHeader(child: Child, count: Int) {
    val spacing = GiftTheme.spacing
    val colors = GiftTheme.colors
    val description = pluralStringResource(R.plurals.search_group_description, count, child.firstName, count)
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .clearAndSetSemantics {
                heading()
                contentDescription = description
            },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = ScreenContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = spacing.xl)
                .padding(top = spacing.m, bottom = spacing.xs),
        ) {
            ChildAvatar(name = child.firstName, emoji = child.avatarEmoji, colorName = child.avatarColor, size = 28.dp, photoUrl = child.avatarUrl)
            Text(child.firstName, style = GiftTheme.type.headline, color = colors.textPrimary)
            Text(count.toString(), style = GiftTheme.type.captionBold.tabular(), color = colors.textSecondary)
        }
    }
}

/** Puces défilantes, du bord gauche au bord droit de l'écran ; la première s'aligne sur la marge d'écran. */
@Composable
private fun FilterChips(filters: SearchFilters, countries: List<String>, actions: SearchActions) {
    val spacing = GiftTheme.spacing
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        contentPadding = PaddingValues(horizontal = spacing.xl),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "available") {
            FilterChip(
                text = stringResource(R.string.search_filter_available),
                selected = filters.onlyAvailable,
                role = Role.Checkbox,
                onClick = actions.onToggleOnlyAvailable,
            )
        }
        item(key = "all") {
            FilterChip(
                text = stringResource(R.string.search_filter_all_countries),
                selected = filters.country == null,
                role = Role.RadioButton,
                onClick = { actions.onSelectCountry(null) },
            )
        }
        items(countries, key = { it }) { code ->
            val flag = Countries.all.firstOrNull { it.code == code }?.flag.orEmpty()
            FilterChip(
                text = "$flag ${Countries.name(code)}".trim(),
                selected = filters.country == code,
                role = Role.RadioButton,
                onClick = { actions.onSelectCountry(code) },
            )
        }
    }
}

/**
 * Puce de filtre (iOS : `chip`) : capsule de 36 dp dans une cible de 48 dp. Active : fond `primary`, coche et texte
 * `onPrimary` ; inactive : `surface` bordée `separator`. « Disponibles » est une case à cocher TalkBack, les pays
 * des boutons radio. Fondu de 150 ms, instantané si animations réduites.
 */
@Composable
private fun FilterChip(text: String, selected: Boolean, role: Role, onClick: () -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val spec = if (GiftTheme.reduceMotion) snap<androidx.compose.ui.graphics.Color>() else tween(150)
    val fill by animateColorAsState(if (selected) colors.primary else colors.surface, spec, label = "chipFill")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.textPrimary, spec, label = "chipContent")
    val border by animateColorAsState(if (selected) colors.primary else colors.separator, spec, label = "chipBorder")
    val source = remember { MutableInteractionSource() }
    val interaction = if (role == Role.Checkbox) {
        Modifier.toggleable(value = selected, interactionSource = source, indication = null, role = role, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, interactionSource = source, indication = null, role = role, onClick = onClick)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = HitTarget.minimum)
            .fcPressable(source, CapsuleShape)
            .then(interaction),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 36.dp.scaledByFont())
                .background(fill, CapsuleShape)
                .border(1.dp, border, CapsuleShape)
                .padding(horizontal = spacing.m, vertical = spacing.xs),
        ) {
            if (selected) {
                Icon(GiftIcons.Checkmark, contentDescription = null, tint = content, modifier = Modifier.size(type.captionBold.glyphSize(1.2f)))
            }
            Text(text, style = type.captionBold, color = content, maxLines = 1)
        }
    }
}

// region Aperçus

private object SearchPreviewData {
    private val leo = ch.jonathanlemaine.giftmanager.ui.gifts.GiftPreviewData.leo
    private val zoe = ch.jonathanlemaine.giftmanager.ui.gifts.GiftPreviewData.zoe
    private val items = ch.jonathanlemaine.giftmanager.ui.gifts.GiftPreviewData.familyItems
    private val parentItems = ch.jonathanlemaine.giftmanager.ui.gifts.GiftPreviewData.parentItems

    private fun link(item: WishItem) =
        ItemLink(item.id, item.id, "https://www.galaxus.ch", "Galaxus", "CH", BigDecimal("129.00"), "CHF")

    val groups = listOf(
        SearchGroup(leo, isParentList = true, results = parentItems.take(3).map { SearchResult(it, link(it), null) }),
        SearchGroup(
            zoe,
            isParentList = false,
            results = listOf(
                SearchResult(items[0], link(items[0]), GiftStatus.AVAILABLE),
                SearchResult(items[1], link(items[1]), GiftStatus.POT),
                SearchResult(items[3], null, GiftStatus.TAKEN),
            ),
        ),
    )
    val state = SearchUiState(groups = groups, countries = listOf("CH", "FR", "DE"), hasLoaded = true)
}

private val previewActions = SearchActions({}, {}, {}, {}, {}, {})

@GiftPreviews
@Composable
private fun SearchScreenPreview() = ScreenPreviewFrame(height = 1300.dp) {
    SearchScreen(SearchPreviewData.state.copy(filters = SearchFilters(onlyAvailable = true, country = "CH")), previewActions)
}

@GiftPreviews
@Composable
private fun SearchEmptyPreview() = ScreenPreviewFrame(height = 900.dp) {
    SearchScreen(SearchUiState(filters = SearchFilters(query = "trottinette"), countries = listOf("CH"), hasLoaded = true), previewActions)
}

@GiftPreviews
@Composable
private fun SearchLoadingPreview() = ScreenPreviewFrame {
    SearchScreen(SearchUiState(), previewActions)
}

// endregion
