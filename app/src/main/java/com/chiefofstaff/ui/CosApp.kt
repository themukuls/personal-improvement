package com.chiefofstaff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chiefofstaff.AppContainer
import com.chiefofstaff.capture.SpeechCaptureController
import com.chiefofstaff.ui.components.BottomBar
import com.chiefofstaff.ui.components.Destination
import com.chiefofstaff.ui.components.GradientDot
import com.chiefofstaff.ui.screens.CloseScreen
import com.chiefofstaff.ui.screens.LookScreen
import com.chiefofstaff.ui.screens.NowScreen
import com.chiefofstaff.ui.screens.TalkScreen
import com.chiefofstaff.ui.theme.ChiefOfStaffTheme
import com.chiefofstaff.ui.theme.Palette
import com.chiefofstaff.ui.vm.CloseViewModel
import com.chiefofstaff.ui.vm.LookViewModel
import com.chiefofstaff.ui.vm.NowViewModel
import com.chiefofstaff.ui.vm.TalkViewModel
import kotlinx.coroutines.launch

/**
 * The whole app: one Scaffold, the four destinations, the neumorphic bottom bar, and the
 * hold-to-talk capture overlay. Navigation depth never exceeds 2 (UX-07): every destination is one
 * tap from every other, and the primary action — voice — is always present in the bottom bar.
 */
@Composable
fun CosApp(container: AppContainer, startInCapture: Boolean = false) {
    ChiefOfStaffTheme {
        var current by remember { mutableStateOf(Destination.Now) }

        val nowVm: NowViewModel = viewModel(factory = viewModelFactory { initializer { NowViewModel(container) } })
        val talkVm: TalkViewModel = viewModel(factory = viewModelFactory { initializer { TalkViewModel(container) } })
        val closeVm: CloseViewModel = viewModel(factory = viewModelFactory { initializer { CloseViewModel(container) } })
        val lookVm: LookViewModel = viewModel(factory = viewModelFactory { initializer { LookViewModel(container) } })

        val scope = rememberCoroutineScope()
        val speech = remember { SpeechCaptureController(container.appContext) }
        val speechState by speech.state.collectAsStateWithLifecycle()
        var listening by remember { mutableStateOf(false) }

        // Route a finished transcript into the capture pipeline (CAP-01).
        LaunchedEffect(speechState) {
            (speechState as? SpeechCaptureController.State.Final)?.let { final ->
                scope.launch { container.captureManager.captureVoice(final.text) }
                speech.reset()
                listening = false
            }
        }
        DisposableEffect(Unit) { onDispose { speech.stop() } }

        LaunchedEffect(startInCapture) {
            if (startInCapture) { listening = true; speech.start() }
        }

        Scaffold(
            containerColor = Palette.Base,
            bottomBar = {
                BottomBar(
                    current = current,
                    onSelect = { current = it },
                    onHoldStart = { listening = true; speech.start() },
                    onHoldEnd = { speech.stop() },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (current) {
                    Destination.Now -> {
                        val s by nowVm.state.collectAsStateWithLifecycle()
                        NowScreen(
                            state = s,
                            onToggle = nowVm::toggleDone,
                            onAddCommitment = { current = Destination.Talk },
                            onAnticipationRated = nowVm::dismissAnticipation,
                            onSetEnergy = nowVm::setEnergy,
                        )
                    }
                    Destination.Talk -> {
                        val s by talkVm.state.collectAsStateWithLifecycle()
                        TalkScreen(state = s, onSend = talkVm::send, onSetMode = talkVm::setMode, onSave = talkVm::saveToMemory)
                    }
                    Destination.Close -> {
                        val s by closeVm.state.collectAsStateWithLifecycle()
                        CloseScreen(state = s, onVerdict = closeVm::verdict)
                    }
                    Destination.Look -> {
                        val s by lookVm.state.collectAsStateWithLifecycle()
                        LookScreen(state = s, onQuery = lookVm::onQuery, onToggleSection = lookVm::toggle, onSetQuiet = lookVm::setQuiet)
                    }
                }

                if (listening) {
                    ListeningOverlay(
                        partial = (speechState as? SpeechCaptureController.State.Partial)?.text.orEmpty(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningOverlay(partial: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Palette.Base.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            GradientDot(sizeDp = 120)
        }
        Text(
            text = partial.ifBlank { "Listening…" },
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = Palette.Ink,
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 200.dp),
        )
    }
}
