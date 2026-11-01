package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * Cœur « très envie » (`PriorityHeart`) : plein `heart` quand actif, contour `textSecondary` sinon.
 *
 * - Avec [onToggle] : interrupteur (cible 48 dp, retour haptique), TalkBack « Très envie, activé ».
 * - Sans : simple indicateur, même encombrement pour aligner les cartes, TalkBack « Très envie » ou
 *   « Envie normale ».
 */
@Composable
fun PriorityHeart(isOn: Boolean, modifier: Modifier = Modifier, onToggle: ((Boolean) -> Unit)? = null) {
    val box = modifier.sizeIn(minWidth = HitTarget.minimum, minHeight = HitTarget.minimum)
    if (onToggle != null) {
        val haptics = LocalHapticFeedback.current
        val source = remember { MutableInteractionSource() }
        val label = stringResource(R.string.heart_label)
        val state = stringResource(if (isOn) R.string.heart_state_on else R.string.heart_state_off)
        Box(
            contentAlignment = Alignment.Center,
            modifier = box
                .fcPressable(source, CircleShape, pressedScale = 0.85f)
                .toggleable(
                    value = isOn,
                    interactionSource = source,
                    indication = null,
                    role = Role.Switch,
                    onValueChange = { on ->
                        haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onToggle(on)
                    },
                )
                .semantics {
                    contentDescription = label
                    stateDescription = state
                },
        ) { HeartGlyph(isOn) }
    } else {
        val label = stringResource(if (isOn) R.string.heart_label else R.string.heart_label_off)
        Box(contentAlignment = Alignment.Center, modifier = box.clearAndSetSemantics { contentDescription = label }) {
            HeartGlyph(isOn)
        }
    }
}

@Composable
private fun HeartGlyph(isOn: Boolean) {
    val colors = GiftTheme.colors
    val size = GiftTheme.type.callout.glyphSize(1.25f)
    Crossfade(
        targetState = isOn,
        animationSpec = if (GiftTheme.reduceMotion) snap() else tween(180),
        label = "heart",
    ) { on ->
        Icon(
            imageVector = if (on) GiftIcons.Heart else GiftIcons.HeartOutline,
            contentDescription = null,
            tint = if (on) colors.heart else colors.textSecondary,
            modifier = Modifier.size(size),
        )
    }
}

@GiftPreviews
@Composable
private fun PriorityHeartPreview() = PreviewFrame {
    var on by remember { mutableStateOf(true) }
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.l), modifier = Modifier.fcCard()) {
        PriorityHeart(isOn = on, onToggle = { on = it })
        PriorityHeart(isOn = true)
        PriorityHeart(isOn = false)
    }
}
