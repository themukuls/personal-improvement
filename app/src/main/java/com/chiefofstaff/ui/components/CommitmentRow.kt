package com.chiefofstaff.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    NeuCard(modifier = modifier, cornerRadius = 20, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
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
