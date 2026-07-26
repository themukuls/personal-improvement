package com.chiefofstaff.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chiefofstaff.AppContainer
import com.chiefofstaff.system.ProviderConfig
import com.chiefofstaff.ui.components.CosTextField
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuButtonNeutral
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * AI & providers. Keys are held in the Keystore-guarded encrypted store (never in the APK, never
 * committed). With no key set the app still runs on the deterministic paths + offline stub (P11);
 * adding one enables the reasoning tasks. The Router picks among whatever keys are present by
 * tier / cost / availability. Newly entered keys take effect on the next app launch.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config = remember { ProviderConfig(context) }
    val scope = rememberCoroutineScope()

    var groq by remember { mutableStateOf(config.groqKey) }
    var claude by remember { mutableStateOf(config.claudeKey) }
    var openai by remember { mutableStateOf(config.openAiKey) }
    var saved by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Base)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        ScreenTopBar(title = "AI & providers", onBack = onBack)
        Spacer(Modifier.height(14.dp))

        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "The app works with no key at all — rituals run on the offline fallback. Add a key to " +
                    "turn on reasoning (planning, extraction, conversation). Keys are stored encrypted " +
                    "on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkMuted,
            )
        }

        KeyField(
            label = "GROQ API KEY",
            hint = "gsk_… · from console.groq.com",
            value = groq,
            onChange = { groq = it; saved = false },
        )
        KeyField(
            label = "CLAUDE API KEY",
            hint = "sk-ant-… · from console.anthropic.com",
            value = claude,
            onChange = { claude = it; saved = false },
        )
        KeyField(
            label = "OPENAI API KEY",
            hint = "sk-… · from platform.openai.com",
            value = openai,
            onChange = { openai = it; saved = false },
        )

        Spacer(Modifier.height(20.dp))
        NeuButton(
            text = if (saved) "Saved ✓" else "Save keys",
            onClick = {
                config.groqKey = groq.trim()
                config.claudeKey = claude.trim()
                config.openAiKey = openai.trim()
                saved = true
                testResult = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        NeuButtonNeutral(
            text = if (testing) "Testing…" else "Test connection",
            onClick = {
                if (testing) return@NeuButtonNeutral
                // Save first so the test uses what's on screen, then hit the provider for real.
                config.groqKey = groq.trim()
                config.claudeKey = claude.trim()
                config.openAiKey = openai.trim()
                testing = true
                testResult = null
                scope.launch {
                    testResult = runCatching { container.testConnection() }.getOrElse { "Test failed: ${it.message}" }
                    testing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        testResult?.let {
            Spacer(Modifier.height(12.dp))
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Keys apply immediately — no restart needed. Use Test connection to confirm a key works.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.InkFaint,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun KeyField(label: String, hint: String, value: String, onChange: (String) -> Unit) {
    Spacer(Modifier.height(20.dp))
    SectionLabel(label)
    Spacer(Modifier.height(8.dp))
    CosTextField(
        value = value,
        onValueChange = onChange,
        placeholder = hint,
        modifier = Modifier.fillMaxWidth(),
    )
}
