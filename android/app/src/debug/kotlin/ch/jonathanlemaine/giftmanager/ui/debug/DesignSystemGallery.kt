package ch.jonathanlemaine.giftmanager.ui.debug

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import ch.jonathanlemaine.giftmanager.ui.auth.GoogleSignInButton
import ch.jonathanlemaine.giftmanager.ui.components.AvatarStack
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.CountryFlag
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.EventRow
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCPillLabel
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.FeaturePill
import ch.jonathanlemaine.giftmanager.ui.components.GiftCard
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftStatus
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.IndicativeRateNote
import ch.jonathanlemaine.giftmanager.ui.components.PotProgressCard
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.PriorityHeart
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.SegmentedTabs
import ch.jonathanlemaine.giftmanager.ui.components.StatusBadge
import ch.jonathanlemaine.giftmanager.ui.components.StoreLinkRow
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.ThemeTokens
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * Galerie de revue du design system (debug uniquement, `DesignSystemGallery.swift`) : tokens et composants
 * dans leurs états, sur données factices. La barre du haut bascule clair / sombre et règle la taille du
 * texte de 100 à 200 % pour éprouver les grandes polices sans toucher aux réglages du téléphone ; elle-même
 * reste à taille normale pour rester utilisable.
 */
@Composable
fun DesignSystemGallery(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val systemDark = isSystemInDarkTheme()
    var dark by rememberSaveable { mutableStateOf(systemDark) }
    var fontScale by rememberSaveable { mutableFloatStateOf(1f) }

    GiftManagerTheme(darkTheme = dark) {
        Column(modifier = modifier.fcScreenBackground().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GalleryToolbar(onBack, dark, { dark = it }, fontScale, { fontScale = it })
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GalleryContent()
            }
        }
    }
}

@Composable
private fun GalleryToolbar(
    onBack: () -> Unit,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val back = stringResource(R.string.gallery_back)
    val scaleLabel = stringResource(R.string.gallery_font_scale)
    val scaleValue = stringResource(R.string.gallery_font_scale_value, (fontScale * 100).roundToInt())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = spacing.s, vertical = spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HitTarget.minimum)
                    .fcClickable(onClick = onBack, shape = CircleShape)
                    .semantics { contentDescription = back },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.textPrimary)
            }
            Text(
                text = stringResource(R.string.gallery_subtitle),
                style = type.headline,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier
                    .heightIn(min = HitTarget.minimum)
                    .toggleable(value = dark, role = Role.Switch, onValueChange = onDarkChange)
                    .padding(horizontal = spacing.s),
            ) {
                Text(stringResource(R.string.gallery_dark), style = type.callout, color = colors.textPrimary)
                Switch(checked = dark, onCheckedChange = null)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            modifier = Modifier.padding(horizontal = spacing.m),
        ) {
            Text(scaleLabel, style = type.caption, color = colors.textSecondary)
            Slider(
                value = fontScale,
                onValueChange = { onFontScaleChange((it * 10).roundToInt() / 10f) },
                valueRange = 1f..2f,
                steps = 9,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = scaleLabel
                        stateDescription = scaleValue
                    },
            )
            Text(scaleValue, style = type.caption, color = colors.textSecondary, modifier = Modifier.width(40.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
}

@Composable
private fun GalleryContent() {
    val spacing = GiftTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl, vertical = spacing.l),
    ) {
        Header()
        LoadingSection()
        ColorsSection()
        TypographySection()
        GiftCardsSection()
        PotSection()
        EventsSection()
        StoreLinksSection()
        ButtonsSection()
        ControlsSection()
        AvatarsSection()
        FeaturesSection()
        EmptyStateSection()
    }
}

// region Sections

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs)) {
        Text(
            stringResource(R.string.gallery_title),
            style = GiftTheme.type.largeTitle,
            color = GiftTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.gallery_subtitle), style = GiftTheme.type.body, color = GiftTheme.colors.textSecondary)
    }
}

@Composable
private fun LoadingSection() = GallerySection(stringResource(R.string.gallery_section_loading)) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        GiftLoadingView(size = 72.dp)
        GiftLoadingView(size = 44.dp, label = "Chargement…")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorsSection() = GallerySection(stringResource(R.string.gallery_section_colors)) {
    val tokens = if (GiftTheme.colors.isDark) ThemeTokens.Dark else ThemeTokens.Light
    val spacing = GiftTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        ThemeTokens.names.forEach { name ->
            val argb = tokens.byName(name) ?: return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.width(96.dp)) {
                val shape = androidx.compose.foundation.shape.RoundedCornerShape(GiftTheme.radius.thumb / 2)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Color(argb), shape)
                        .border(1.dp, GiftTheme.colors.separator, shape),
                )
                Text(name, style = GiftTheme.type.captionBold, color = GiftTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "#%06X".format(argb and 0xFFFFFF),
                    style = GiftTheme.type.caption,
                    color = GiftTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun TypographySection() = GallerySection(stringResource(R.string.gallery_section_type)) {
    val type = GiftTheme.type
    val color = GiftTheme.colors.textPrimary
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        Text("Notre famille", style = type.largeTitle, color = color)
        Text("Bienvenue sur Gift Manager", style = type.title, color = color)
        Text("LEGO Technic McLaren F1", style = type.headline, color = color)
        Text("Organisez les cadeaux de toute la famille en toute simplicité.", style = type.body, color = color)
        Text("Liste (8) · Possède déjà (3)", style = type.callout, color = color)
        Text("8 ans · Ses envies", style = type.caption, color = color)
        Text("Disponible", style = type.captionBold, color = color)
    }
}

@Composable
private fun GiftCardsSection() = GallerySection(stringResource(R.string.gallery_section_cards)) {
    GiftCard("LEGO Technic McLaren F1", null, "CHF 199.–", "Galaxus", "CH", isFavorite = true, status = GiftStatus.AVAILABLE, onClick = {})
    GiftCard("PlayStation 5 Pro", null, "CHF 799.–", "Digitec", "CH", isFavorite = false, status = GiftStatus.TAKEN, onClick = {})
    GiftCard(
        "Casque Sony WH-1000XM5", "https://invalid.invalid/x.png", "€ 299,00", "Amazon.fr", "FR",
        isFavorite = true, status = GiftStatus.MINE, approxPriceText = "≈ 277 CHF", onClick = {},
    )
    GiftCard("Nintendo Switch OLED", null, "CHF 349.–", "Fnac", "CH", isFavorite = false, status = GiftStatus.OWNED, onClick = {})
    GiftCard("Vélo d'enfant 20 pouces", null, "CHF 329.–", "Decathlon", "CH", isFavorite = false, status = GiftStatus.POT, onClick = {})
    GiftCard(
        "Maillot PSG 2025 (vue parent, mode surprise)", null, "CHF 89.–", "Nike", "CH",
        isFavorite = true, status = null, onClick = {},
    )
}

@Composable
private fun PotSection() = GallerySection(stringResource(R.string.gallery_section_pot)) {
    PotProgressCard(PotProgress(BigDecimal(120), "CHF", BigDecimal(199), 3), myShareText = "CHF 40.00")
    PotProgressCard(PotProgress(BigDecimal(210), "CHF", BigDecimal(199), 5))
    PotProgressCard(PotProgress(BigDecimal(60), "EUR", null, 2))
}

@Composable
private fun EventsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        SectionHeader(
            stringResource(R.string.gallery_section_events),
            actionIcon = GiftIcons.Add,
            onAction = {},
            actionLabel = "Ajouter un événement",
        )
        Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
            EventRow("Anniversaire de Léo", "12 mars 2026", "8 ans", GiftEventKind.BIRTHDAY, listOf("Claire", "Paul", "Mamie", "Papi"), onClick = {})
            EventRow("Noël 2026", "25 décembre 2026", null, GiftEventKind.CHRISTMAS, listOf("Léo", "Emma", "Noé"), onClick = {})
            EventRow("Fête des grands-parents", "5 octobre 2026", "Chez Mamie", GiftEventKind.OTHER, emptyList(), onClick = {})
        }
    }
}

@Composable
private fun StoreLinksSection() = GallerySection(stringResource(R.string.gallery_section_links)) {
    StoreLinkRow("Galaxus (CH)", "CH", "CHF 199.–", onClick = {})
    StoreLinkRow("Amazon.fr (FR)", "FR", "€ 199,99", onClick = {}, approxPriceText = "≈ 185 CHF")
    StoreLinkRow("Boutique inconnue", null, null, onClick = {})
    IndicativeRateNote(rateDateText = "2 octobre")
}

@Composable
private fun ButtonsSection() = GallerySection(stringResource(R.string.gallery_section_buttons)) {
    PrimaryButton("Continuer", onClick = {}, trailingIcon = GiftIcons.ChevronRight)
    PrimaryButton("Ajouter à la liste", onClick = {}, isLoading = true)
    PrimaryButton("Continuer", onClick = {}, enabled = false)
    SecondaryButton("Ajouter à la liste", onClick = {}, leadingIcon = GiftIcons.Gift)
    TextLinkButton("Modifier les informations", onClick = {}, modifier = Modifier.align(Alignment.CenterHorizontally))
    FCPillLabel("Partager l'invitation", icon = GiftIcons.Share, modifier = Modifier.fcClickable(onClick = {}))
    GoogleSignInButton(onClick = {})
    GoogleSignInButton(onClick = {}, isLoading = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsSection() = GallerySection(stringResource(R.string.gallery_section_controls)) {
    val spacing = GiftTheme.spacing
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Léo") }
    var favorite by rememberSaveable { mutableStateOf(true) }

    SegmentedTabs(tab, { tab = it }, listOf("Liste (8)", "Possède déjà (3)", "Idées (2)"))
    FCTextField(
        "Lien du cadeau", url, { url = it },
        leadingIcon = GiftIcons.Search,
        placeholder = "https://www.galaxus.ch/…",
        isLabelHidden = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    FCTextField("Prénom", name, { name = it }, placeholder = "Ex. Léo")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        GiftStatus.entries.forEach { StatusBadge(it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
        PriorityHeart(isOn = favorite, onToggle = { favorite = it })
        PriorityHeart(isOn = false)
    }
    FCNotice(GiftIcons.EyeSlash, "Mode surprise : tu ne vois pas ce qui a été réservé.", tone = FCNoticeTone.SURPRISE)
    FCNotice(GiftIcons.Archive, "Événement passé : fiche archivée.")
    FCNotice(GiftIcons.Warning, "Les parents l'ont noté comme déjà possédé.", tone = FCNoticeTone.WARNING)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.m), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        listOf("CH", "FR", "DE", "IT", "BE", "??").forEach { CountryFlag(it, style = GiftTheme.type.title) }
    }
}

@Composable
private fun AvatarsSection() = GallerySection(stringResource(R.string.gallery_section_avatars)) {
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
        ChildAvatar("Léo", "🦊", "pastelPeach", size = 56.dp)
        ChildAvatar("Emma", null, "pastelPink", size = 56.dp)
        ChildAvatar("Noé", "🐻", "pastelMint", size = 56.dp)
        ChildAvatar("Lou", null, "pastelLavender", size = 56.dp, photoUrl = "https://invalid.invalid/lou.jpg")
    }
    AvatarStack(listOf("Jonathan", "Claire", "Paul", "Mamie", "Papi", "Léo", "Emma", "Noé", "Lou", "Tom", "Zoé", "Max", "Inès", "Hugo"))
    Text("12 membres · 3 foyers", style = GiftTheme.type.caption, color = GiftTheme.colors.textSecondary)
}

@Composable
private fun FeaturesSection() = GallerySection(stringResource(R.string.gallery_section_features)) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
        FeaturePill(GiftIcons.Group, colors.heart, "Groupes familiaux", "Plusieurs foyers, plusieurs pays", Modifier.weight(1f), colors.pastelPink)
        FeaturePill(GiftIcons.Gift, colors.availableFg, "Listes par événement", "Anniversaire, Noël, et plus", Modifier.weight(1f), colors.pastelMint)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl), modifier = Modifier.padding(top = spacing.s)) {
        FeaturePill(GiftIcons.Link, colors.mineFg, "Toutes les boutiques", "Amazon, Galaxus, Fnac, etc.", Modifier.weight(1f), colors.pastelBlue)
        FeaturePill(GiftIcons.Lock, colors.accentAmber, "Surprise garantie", "« Déjà pris » sans savoir par qui", Modifier.weight(1f), colors.pastelPeach)
    }
}

@Composable
private fun EmptyStateSection() = GallerySection(stringResource(R.string.gallery_section_empty)) {
    EmptyStateView(
        Illustration.EMPTY_BOX,
        "Aucun cadeau pour l'instant",
        "Ajoutez une première envie depuis n'importe quelle boutique.",
        modifier = Modifier.fcCard(),
        actionLabel = "Ajouter un cadeau",
        onAction = {},
    )
}

// endregion

/** Titre de section (titre TalkBack) + contenu. */
@Composable
private fun GallerySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
        Text(
            text = title,
            style = GiftTheme.type.headline,
            color = GiftTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Preview(name = "Clair", widthDp = 360, heightDp = 2400)
@Preview(name = "Sombre", widthDp = 360, heightDp = 2400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DesignSystemGalleryPreview() = DesignSystemGallery(onBack = {})
