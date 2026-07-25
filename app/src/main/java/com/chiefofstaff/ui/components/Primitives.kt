package com.chiefofstaff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiefofstaff.ui.theme.AvatarGradient
import com.chiefofstaff.ui.theme.DecorativeGradient
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface

/** ALL-CAPS mono section label: LOOKING AHEAD, TODAY, NOT TODAY, RIGHT NOW. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = Mono.SectionLabel,
        color = Palette.InkFaint,
        modifier = modifier,
    )
}

/** A raised neumorphic card — the workhorse surface of every screen. */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 22,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .neuSurface(cornerRadius = cornerRadius)
            .padding(padding),
    ) { content() }
}

/**
 * A card whose right side bleeds into the decorative pastel gradient — the anticipation
 * ("LOOKING AHEAD") card and the 1c hero window. The gradient is masked to a soft overlay so
 * text on the left stays legible.
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 22,
    brush: Brush = DecorativeGradient,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .neuSurface(cornerRadius = cornerRadius)
            .clip(RoundedCornerShape(cornerRadius.dp)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush),
        )
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

/** Gradient avatar / affordance dot used top-left and on the hold-to-talk control. */
@Composable
fun GradientDot(sizeDp: Int = 34, brush: Brush = AvatarGradient, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 2).dp))
            .background(brush),
    )
}

/** Text truncation helper used by list rows. */
@Composable
fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(text = text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}
