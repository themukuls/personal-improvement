package com.chiefofstaff.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chiefofstaff.AppContainer
import com.chiefofstaff.BuildConfig
import com.chiefofstaff.system.UserProfile
import com.chiefofstaff.ui.components.CosTextField
import com.chiefofstaff.ui.components.FOCUS_OPTIONS
import com.chiefofstaff.ui.components.FocusChip
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.AvatarGradient
import com.chiefofstaff.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * The user's profile: reached from the avatar in the Now header. Edits the "about you" gathered at
 * onboarding (name, focus areas, intention) and gathers the other account-level options in one
 * place — AI & API keys, a data export, and about — without a sprawling settings screen (§15).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val profile = remember { UserProfile(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(profile.name) }
    var intention by remember { mutableStateOf(profile.intention) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(profile.focusAreas) } }
    var saved by remember { mutableStateOf(false) }
    var exportMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        ScreenTopBar(title = "Profile", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        // Avatar + name headline.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(name = name, size = 64)
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (name.isBlank()) "You" else name,
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Ink,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("WHAT SHOULD I CALL YOU?")
        Spacer(Modifier.height(10.dp))
        CosTextField(
            value = name,
            onValueChange = { name = it; saved = false },
            placeholder = "First name",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(22.dp))
        SectionLabel("WHAT YOU WANT TO STAY ON TOP OF")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FOCUS_OPTIONS.forEach { (key, label) ->
                FocusChip(
                    label = label,
                    selected = key in selected,
                    onClick = {
                        if (key in selected) selected.remove(key) else selected.add(key)
                        saved = false
                    },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("ON YOUR MIND")
        Spacer(Modifier.height(10.dp))
        CosTextField(
            value = intention,
            onValueChange = { intention = it; saved = false },
            placeholder = "e.g. keep my mornings for deep work",
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        NeuButton(
            text = if (saved) "Saved ✓" else "Save changes",
            onClick = {
                profile.save(name, selected.toSet(), intention)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Account options ---
        Spacer(Modifier.height(28.dp))
        SectionLabel("OPTIONS")
        Spacer(Modifier.height(10.dp))
        OptionRow(
            icon = Icons.Outlined.Tune,
            title = "AI & API keys",
            subtitle = "Add a Groq / Claude / OpenAI key to enable reasoning",
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(10.dp))
        OptionRow(
            icon = Icons.Outlined.Backup,
            title = "Export a backup",
            subtitle = exportMsg ?: "Write a full JSON snapshot to app storage",
            onClick = {
                scope.launch {
                    exportMsg = "Exporting…"
                    val file = runCatching { container.backup.exportJson() }.getOrNull()
                    exportMsg = if (file != null) "Saved ${file.name}" else "Export failed"
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        OptionRow(
            icon = Icons.Outlined.Info,
            title = "About",
            subtitle = "Chief of Staff v${BuildConfig.VERSION_NAME} · all data stays on this device",
            onClick = {},
            showChevron = false,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    NeuCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Palette.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.InkMuted)
            }
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Palette.InkFaint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Circular gradient avatar showing the user's initial (or a dot when unnamed). */
@Composable
fun ProfileAvatar(name: String, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size.dp).clip(CircleShape).background(AvatarGradient),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().firstOrNull()?.uppercase().orEmpty()
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

/** Shared back-bar for the overlay screens (Profile / Settings). */
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Palette.Ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
    }
}
