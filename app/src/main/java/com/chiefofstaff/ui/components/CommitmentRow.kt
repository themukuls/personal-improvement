package com.chiefofstaff.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiefofstaff.ui.theme.Mono
import com.chiefofstaff.ui.theme.Palette

/**
 * A single TODAY row, matching Direction 1a exactly: a mono time (or "wait") in the left gutter,
 * the title, a neutral context subline ("skipped twice last week", "deferred once · predicted
 * 15:20", "promised Friday · unchased 4 days"), and a hollow circular check on the right. The
 * context line is factual and never uses guilt language (RES-06).
 */
@Composable
fun CommitmentRow(
    time: String,
    title: String,
    context: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuCard(modifier = modifier, cornerRadius = 20, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = time,
                style = Mono.Time,
                color = Palette.InkFaint,
                modifier = Modifier.width(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                if (!context.isNullOrBlank()) {
                    Text(
                        context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.InkFaint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            NeuCheckbox(checked = checked, onToggle = onToggle)
        }
    }
}

/**
 * A TODAY item as a square-ish tile for the 2-column grid on the Now screen — echoing the
 * reference design's overview grid. A soft accent time-chip top-left, the check top-right, then
 * the title and a factual context subline. Same content as [CommitmentRow], laid out for a grid.
 */
@Composable
fun CommitmentTile(
    time: String,
    title: String,
    context: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuCard(modifier = modifier, cornerRadius = 22, padding = PaddingValues(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 108.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    style = Mono.Time,
                    color = Palette.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.AccentSoft)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Spacer(Modifier.weight(1f))
                NeuCheckbox(checked = checked, onToggle = onToggle)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!context.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    context,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
