package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/** Position d'un onglet dans la rangée, en pixels. */
private data class TabBounds(val left: Float, val width: Float)

/**
 * Onglets soulignés (`SegmentedTabs`) : « Liste (8) | Possède déjà (3) | Idées (2) ».
 *
 * Largeurs égales tant que les libellés tiennent ; aux grandes tailles de police, la rangée devient
 * défilable horizontalement plutôt que de tronquer. Le soulignement glisse vers l'onglet choisi (ressort,
 * instantané si animations réduites). TalkBack : « Liste (8), onglet, sélectionné, 1 sur 3 ».
 */
@Composable
fun SegmentedTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    titles: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val density = LocalDensity.current
    val gapPx = with(density) { GiftTheme.spacing.l.roundToPx() }
    val underlinePx = with(density) { 2.5.dp.toPx() }
    val separatorPx = with(density) { 1.dp.toPx() }
    val scroll = rememberScrollState()
    val reduceMotion = GiftTheme.reduceMotion

    var bounds by remember(titles) { mutableStateOf<List<TabBounds>>(emptyList()) }
    val indicatorLeft = remember { Animatable(0f) }
    val indicatorWidth = remember { Animatable(0f) }
    var placedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, bounds, reduceMotion) {
        val target = bounds.getOrNull(selectedIndex) ?: return@LaunchedEffect
        if (!placedOnce || reduceMotion) {
            indicatorLeft.snapTo(target.left)
            indicatorWidth.snapTo(target.width)
            placedOnce = true
        } else {
            val spec = spring<Float>(dampingRatio = 0.85f, stiffness = 440f)
            kotlinx.coroutines.coroutineScope {
                launch { indicatorLeft.animateTo(target.left, spec) }
                launch { indicatorWidth.animateTo(target.width, spec) }
            }
        }
        // Onglet choisi toujours visible quand la rangée défile.
        if (scroll.maxValue > 0) {
            val start = (target.left - gapPx).toInt().coerceAtLeast(0)
            if (reduceMotion) scroll.scrollTo(start) else scroll.animateScrollTo(start)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(colors.separator, topLeft = Offset(0f, size.height - separatorPx), size = Size(size.width, separatorPx))
            },
    ) {
        val available = constraints.maxWidth
        Layout(
            modifier = Modifier
                .horizontalScroll(scroll)
                .selectableGroup()
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = titles.size) }
                .drawWithContent {
                    drawContent()
                    if (indicatorWidth.value > 0f) {
                        drawRoundRect(
                            color = colors.primary,
                            topLeft = Offset(indicatorLeft.value, size.height - underlinePx),
                            size = Size(indicatorWidth.value, underlinePx),
                            cornerRadius = CornerRadius(underlinePx / 2, underlinePx / 2),
                        )
                    }
                },
            content = {
                titles.forEachIndexed { index, title ->
                    val selected = index == selectedIndex
                    val source = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = HitTarget.minimum)
                            .fcPressable(source, GiftTheme.radius.fieldShape, pressedScale = 1f)
                            .selectable(
                                selected = selected,
                                interactionSource = source,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(index) },
                            )
                            .semantics { collectionItemInfo = CollectionItemInfo(0, 1, index, 1) }
                            .padding(horizontal = GiftTheme.spacing.xs),
                    ) {
                        Text(
                            text = title,
                            style = if (selected) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout,
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val count = measurables.size.coerceAtLeast(1)
            val share = available / count
            // Largeurs égales si chaque libellé tient dans sa part ; sinon largeurs naturelles + défilement.
            val equal = natural.all { it <= share }
            val placeables = measurables.mapIndexed { i, m ->
                val width = if (equal) share else natural[i]
                m.measure(Constraints(minWidth = width, maxWidth = width))
            }
            val gap = if (equal) 0 else gapPx
            val total = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val layoutWidth = if (equal) available else total
            layout(layoutWidth, height) {
                var x = 0
                val placed = ArrayList<TabBounds>(placeables.size)
                placeables.forEach { p ->
                    p.placeRelative(x, 0)
                    placed += TabBounds(x.toFloat(), p.width.toFloat())
                    x += p.width + gap
                }
                if (placed != bounds) bounds = placed
            }
        }
    }
}

@GiftPreviews
@Composable
private fun SegmentedTabsPreview() = PreviewFrame {
    var selected by remember { mutableIntStateOf(0) }
    SegmentedTabs(selected, { selected = it }, listOf("Liste (8)", "Possède déjà (3)", "Idées (2)"))
    SegmentedTabs(1, {}, listOf("Événements", "Membres", "Paramètres"))
}
