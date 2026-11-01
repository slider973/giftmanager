package ch.jonathanlemaine.giftmanager.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftCard
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

// Écran de câblage de la recherche (#75) : fonctionnel, sans travail de design. Le `ui-designer` le reprend.

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
 * Recherche (iOS : `SearchView`) : champ, filtres « Disponibles » et pays, résultats groupés par enfant. Le badge d'une
 * carte est absent pour la liste d'un parent (mode surprise).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(state: SearchUiState, actions: SearchActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val colors = GiftTheme.colors
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = actions.onRefresh,
        modifier = modifier
            .fillMaxSize()
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            contentPadding = PaddingValues(horizontal = spacing.xl, vertical = spacing.l),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") { ScreenTitle(stringResource(R.string.tab_search)) }
            item(key = "field") {
                FCTextField(
                    label = stringResource(R.string.search_field_label),
                    value = state.filters.query,
                    onValueChange = actions.onQueryChange,
                    leadingIcon = GiftIcons.Search,
                    isLabelHidden = true,
                    placeholder = stringResource(R.string.search_field_label),
                )
            }
            item(key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = state.filters.onlyAvailable,
                        onClick = actions.onToggleOnlyAvailable,
                        label = { Text(stringResource(R.string.search_filter_available)) },
                    )
                    FilterChip(
                        selected = state.filters.country == null,
                        onClick = { actions.onSelectCountry(null) },
                        label = { Text(stringResource(R.string.search_filter_all_countries)) },
                    )
                    state.countries.forEach { code ->
                        FilterChip(
                            selected = state.filters.country == code,
                            onClick = { actions.onSelectCountry(code) },
                            label = { Text("${Countries.all.firstOrNull { it.code == code }?.flag.orEmpty()} ${Countries.name(code)}".trim()) },
                        )
                    }
                }
            }
            when {
                !state.hasLoaded -> item(key = "loading") {
                    GiftLoadingView(label = stringResource(R.string.search_loading), modifier = Modifier.fillMaxWidth())
                }
                state.isEmpty -> item(key = "empty") {
                    EmptyStateView(
                        illustration = Illustration.MASCOT_THINKING,
                        title = stringResource(R.string.search_empty_title),
                        message = stringResource(if (state.hasQuery) R.string.search_empty_query else R.string.search_empty_filters),
                    )
                }
            }
            state.groups.forEach { group ->
                item(key = "header-${group.child.id}") {
                    val description = stringResource(R.string.search_group_description, group.child.firstName, group.results.size)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.s),
                        modifier = Modifier.padding(top = spacing.s).semantics(mergeDescendants = true) {
                            heading()
                            contentDescription = description
                        },
                    ) {
                        Text(group.child.firstName, style = GiftTheme.type.headline, color = colors.textPrimary)
                        Text("${group.results.size}", style = GiftTheme.type.captionBold, color = colors.textSecondary)
                    }
                }
                items(group.results, key = { it.item.id }) { result ->
                    GiftCard(
                        title = result.item.title,
                        imageUrl = result.item.imageUrl,
                        priceText = result.link?.priceText,
                        storeText = result.link?.store,
                        countryCode = result.link?.country,
                        isFavorite = result.item.isFavorite,
                        status = result.badge,
                        onClick = { actions.onOpenItem(result.item) },
                    )
                }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}
