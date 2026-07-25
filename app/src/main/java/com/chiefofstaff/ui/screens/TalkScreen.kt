package com.chiefofstaff.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chiefofstaff.data.model.ConversationMode
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.theme.neuSurface
import com.chiefofstaff.ui.vm.ChatLine
import com.chiefofstaff.ui.vm.TalkUiState

/**
 * The Talk surface (§3.2, §10). Full-height thread, empty by default — no suggested prompts
 * cluttering it. This is where planning, thinking and discussion happen with full memory access.
 * Voice is the primary way in (the hold-to-talk control lives in the app bar); typing is here for
 * when speaking isn't possible.
 */
@Composable
fun TalkScreen(
    state: TalkUiState,
    onSend: (String) -> Unit,
    onSetMode: (ConversationMode) -> Unit,
    onSave: (ChatLine) -> Unit,
    onSaveDecision: (ChatLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ModeRow(current = state.mode, onSetMode = onSetMode)
        if (state.lines.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Think out loud. I have the full picture.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.InkGhost,
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.lines) { line ->
                    ChatBubble(line, onSave, onSaveDecision, decideMode = state.mode == ConversationMode.DECIDE)
                }
                if (state.thinking) {
                    item { Text("…", style = MaterialTheme.typography.titleLarge, color = Palette.InkFaint, modifier = Modifier.padding(8.dp)) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Say anything", color = Palette.InkGhost) },
                singleLine = true,
                modifier = Modifier.weight(1f).neuSurface(cornerRadius = 22, elevation = 6.dp, pressed = true),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(input); input = ""
                }),
            )
            Spacer(Modifier.padding(6.dp))
            NeuButton(text = "Send", onClick = { onSend(input); input = "" })
        }
    }
}

@Composable
private fun ModeRow(current: ConversationMode, onSetMode: (ConversationMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConversationMode.entries.forEach { mode ->
            val active = mode == current
            Text(
                text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Palette.Ink else Palette.InkFaint,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (active) Modifier.neuSurface(cornerRadius = 14, elevation = 5.dp, pressed = true) else Modifier)
                    .clickable { onSetMode(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ChatBubble(
    line: ChatLine,
    onSave: (ChatLine) -> Unit,
    onSaveDecision: (ChatLine) -> Unit,
    decideMode: Boolean,
) {
    val isUser = line.role == "user"
    Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        NeuCard(modifier = Modifier.widthIn(max = 300.dp)) {
            Text(
                line.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) Palette.Ink else Palette.InkMuted,
            )
        }
        // CNV-04 fact emission; CNV-09 decision record in Decide mode.
        if (!isUser) {
            val label = when {
                line.saved -> "saved ✓"
                decideMode -> "Save as decision"
                else -> "Save to memory"
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (line.saved) Palette.Accent else Palette.InkFaint,
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (line.saved) Modifier
                        else Modifier.clickable { if (decideMode) onSaveDecision(line) else onSave(line) }
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}
