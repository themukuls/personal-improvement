package com.chiefofstaff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface
import com.chiefofstaff.ui.vm.LookSection
import com.chiefofstaff.ui.vm.LookUiState

/**
 * The Look screen (§3.2, UX-05) — "everything else". A search field over the FTS index at the top,
 * then four sections collapsed by default. This is where ~100 of the 150 features live; it is
 * visited perhaps twice a week and deliberately holds nothing that competes for daily attention.
 */
@Composable
fun LookScreen(
    state: LookUiState,
    onQuery: (String) -> Unit,
    onToggleSection: (LookSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        TextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = { Text("Search everything", color = Palette.InkGhost) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().neuSurface(cornerRadius = 22, elevation = 6.dp, pressed = true),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        if (state.results.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("RESULTS")
            Spacer(Modifier.height(8.dp))
            state.results.forEach { r ->
                NeuCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(r, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        CollapsedSection("TIMELINE", LookSection.TIMELINE, state, onToggleSection) {
            if (state.timeline.isEmpty()) Text("No captures yet.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
            else state.timeline.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        CollapsedSection("TRENDS", LookSection.TRENDS, state, onToggleSection) {
            Text("Trends appear when there is data to trend.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
        }
        CollapsedSection("DOMAINS", LookSection.DOMAINS, state, onToggleSection) {
            Text("Health · Work · Money · People · Home — activate as you go.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
        }
        CollapsedSection("PEOPLE", LookSection.PEOPLE, state, onToggleSection) {
            Text("People appear once people exist.", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CollapsedSection(
    label: String,
    section: LookSection,
    state: LookUiState,
    onToggle: (LookSection) -> Unit,
    content: @Composable () -> Unit,
) {
    val expanded = state.expanded == section
    NeuCard(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle(section) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(label, modifier = Modifier.weight(1f))
                Text(if (expanded) "–" else "+", style = MaterialTheme.typography.titleLarge, color = Palette.InkFaint)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
