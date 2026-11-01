package ch.jonathanlemaine.giftmanager.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/** Onglets de destination ; « + » n'en est pas un (il ouvre une feuille). Ordre d'iOS. */
enum class MainTab { Home, Search, Purchases, Profile }

/** Diamètre du disque « + » central. */
private val AddDiscSize = 48.dp

/**
 * Barre d'onglets (iOS : `MainTabView`) : Accueil · Recherche · « + » · Mes achats · Profil.
 *
 * `NavigationBar` M3 sur `surface` avec un filet `separator` au-dessus ; onglet actif en `primary` sur une
 * pastille `primary` à 12 %, inactifs en `textSecondary` (AA dans les deux apparences). Le « + » central
 * est un disque `primary` plein, croix `onPrimary`, sans état sélectionné : toute la case est la cible
 * (≥ 48 dp), le disque s'enfonce à la pression. TalkBack : « Ajouter un cadeau, bouton ».
 */
@Composable
fun MainNavigationBar(current: MainTab?, onSelect: (MainTab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    Column(modifier = modifier) {
        HorizontalDivider(thickness = 1.dp, color = colors.separator)
        NavigationBar(containerColor = colors.surface, contentColor = colors.textSecondary, tonalElevation = 0.dp) {
            TabItem(MainTab.Home, current, onSelect, R.string.tab_home, GiftIcons.Home)
            TabItem(MainTab.Search, current, onSelect, R.string.tab_search, GiftIcons.Search)
            AddItem(onAdd)
            TabItem(MainTab.Purchases, current, onSelect, R.string.tab_purchases, GiftIcons.Bag)
            TabItem(MainTab.Profile, current, onSelect, R.string.tab_profile, GiftIcons.Profile)
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: MainTab, current: MainTab?, onSelect: (MainTab) -> Unit, label: Int, icon: ImageVector) {
    val colors = GiftTheme.colors
    NavigationBarItem(
        selected = tab == current,
        onClick = { onSelect(tab) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.primary,
            indicatorColor = colors.primary.copy(alpha = 0.12f),
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textSecondary,
        ),
    )
}

@Composable
private fun RowScope.AddItem(onAdd: () -> Unit) {
    val colors = GiftTheme.colors
    val source = remember { MutableInteractionSource() }
    val description = stringResource(R.string.tab_add_description)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onAdd)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onAdd(); true }
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(maxOf(AddDiscSize, HitTarget.minimum))
                .fcPressable(source, CircleShape, pressedScale = 0.9f)
                .background(colors.primary, CircleShape),
        ) {
            Icon(GiftIcons.Add, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@GiftPreviews
@Composable
private fun MainNavigationBarPreview() = PreviewFrame {
    MainNavigationBar(current = MainTab.Home, onSelect = {}, onAdd = {})
    MainNavigationBar(current = MainTab.Profile, onSelect = {}, onAdd = {})
}
