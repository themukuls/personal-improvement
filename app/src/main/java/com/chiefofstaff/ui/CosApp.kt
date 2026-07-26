package com.chiefofstaff.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chiefofstaff.AppContainer
import com.chiefofstaff.capture.SpeechCaptureController
import com.chiefofstaff.ui.components.BottomBar
import com.chiefofstaff.ui.components.Destination
import com.chiefofstaff.ui.components.ListeningOrb
import com.chiefofstaff.ui.screens.CalendarScreen
import com.chiefofstaff.ui.screens.CloseScreen
import com.chiefofstaff.ui.screens.LookScreen
import com.chiefofstaff.ui.screens.NowScreen
import com.chiefofstaff.ui.screens.ProfileScreen
import com.chiefofstaff.ui.screens.SettingsScreen
import com.chiefofstaff.ui.screens.TalkScreen
import com.chiefofstaff.ui.theme.ChiefOfStaffTheme
import com.chiefofstaff.ui.theme.GlassBackdrop
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
        var overlay by remember { mutableStateOf<CosOverlay?>(null) }

        val nowVm: NowViewModel = viewModel(factory = viewModelFactory { initializer { NowViewModel(container) } })
        val talkVm: TalkViewModel = viewModel(factory = viewModelFactory { initializer { TalkViewModel(container) } })
        val closeVm: CloseViewModel = viewModel(factory = viewModelFactory { initializer { CloseViewModel(container) } })
        val lookVm: LookViewModel = viewModel(factory = viewModelFactory { initializer { LookViewModel(container) } })

        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val speech = remember { SpeechCaptureController(container.appContext) }
        val speechState by speech.state.collectAsStateWithLifecycle()
        val inSession by speech.continuous.collectAsStateWithLifecycle()
        val amplitude by speech.amplitude.collectAsStateWithLifecycle()
        var listening by remember { mutableStateOf(false) }
        // CAP-17 — a session accumulates a rough meeting transcript, summarised when it ends.
        val meetingBuffer = remember { StringBuilder() }

        // End a hands-free session: stop listening and, if enough was said, summarise it (CAP-17).
        val endSession = {
            val transcript = meetingBuffer.toString().trim()
            meetingBuffer.clear()
            speech.stop()
            listening = false
            if (transcript.length >= 120) scope.launch { container.meetingSummariser.summarise(transcript) }
        }

        // React to every speech outcome. A single tap-to-talk transcript is captured (CAP-01, never
        // lost — P5) and then handed to the assistant on Talk so it actually responds and acts. In a
        // hands-free session each utterance is captured and buffered for the end-of-session summary.
        // Crucially, Error and Idle also close the overlay — otherwise it hangs open (the old bug).
        LaunchedEffect(speechState) {
            when (val s = speechState) {
                is SpeechCaptureController.State.Final -> {
                    val text = s.text.trim()
                    speech.reset()
                    if (inSession) {
                        if (text.isNotEmpty()) {
                            meetingBuffer.append(text).append(' ')
                            scope.launch { container.captureManager.captureVoice(text) }
                        }
                    } else {
                        listening = false
                        if (text.isNotEmpty()) {
                            current = Destination.Talk       // surface the assistant working on it
                            talkVm.voiceCommand(text)        // interpret + take the action, then reply
                        }
                    }
                }
                is SpeechCaptureController.State.Error -> {
                    val msg = s.message
                    speech.stop()
                    speech.reset()
                    listening = false
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
        DisposableEffect(Unit) { onDispose { speech.stop() } }

        LaunchedEffect(startInCapture) {
            if (startInCapture) { listening = true; speech.start() }
        }

        // Android back closes the overlay (Settings → Profile → main) rather than exiting.
        BackHandler(enabled = overlay != null) {
            overlay = if (overlay == CosOverlay.Settings) CosOverlay.Profile else null
        }

        Box(Modifier.fillMaxSize()) {
        GlassBackdrop(Modifier.matchParentSize())
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                BottomBar(
                    current = current,
                    onSelect = { current = it },
                    listening = listening,
                    // Tap to talk: start listening, or cancel if already listening.
                    onMicTap = {
                        if (listening) { speech.stop(); listening = false }
                        else { listening = true; speech.start() }
                    },
                    // CNV-13 — double-tap toggles a continuous hands-free session.
                    onSessionToggle = {
                        if (inSession) endSession()
                        else { listening = true; speech.startContinuous() }
                    },
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
                            onSetMood = nowVm::setMood,
                            onSetMode = nowVm::setMode,
                            onToggleQuiet = nowVm::toggleQuiet,
                            onOpenProfile = { overlay = CosOverlay.Profile },
                            onOpenCalendar = { overlay = CosOverlay.Calendar },
                            onRelationshipAnswer = nowVm::answerRelationship,
                            onRelationshipSayMore = {
                                nowVm.markRelationshipAnswered()
                                current = Destination.Talk
                            },
                        )
                    }
                    Destination.Talk -> {
                        val s by talkVm.state.collectAsStateWithLifecycle()
                        TalkScreen(
                            state = s,
                            onSend = talkVm::send,
                            onSave = talkVm::saveToMemory,
                            onSaveDecision = talkVm::saveAsDecision,
                        )
                    }
                    Destination.Close -> {
                        val s by closeVm.state.collectAsStateWithLifecycle()
                        CloseScreen(state = s, onVerdict = closeVm::verdict)
                    }
                    Destination.Look -> {
                        val s by lookVm.state.collectAsStateWithLifecycle()
                        LookScreen(
                            state = s,
                            onQuery = lookVm::onQuery,
                            onToggleSection = lookVm::toggle,
                            onSetQuiet = lookVm::setQuiet,
                            onDrop = lookVm::drop,
                            onBankruptcy = lookVm::declareBankruptcy,
                            onChase = lookVm::chase,
                            onResolveWaiting = lookVm::resolveWaiting,
                            onLogContact = lookVm::logContact,
                            onSetMode = lookVm::setMode,
                            onRecordOutcome = lookVm::recordDecisionOutcome,
                            onReschedule = lookVm::rescheduleTomorrow,
                        )
                    }
                }

                if (listening) {
                    ListeningOverlay(
                        partial = (speechState as? SpeechCaptureController.State.Partial)?.text.orEmpty(),
                        inSession = inSession,
                        amplitude = amplitude,
                        onDismiss = {
                            if (inSession) endSession()
                            else { speech.stop(); speech.reset(); listening = false }
                        },
                    )
                }
            }
        }

            // Full-screen overlays above the main scaffold — reached from the profile avatar.
            when (overlay) {
                CosOverlay.Profile -> ProfileScreen(
                    container = container,
                    onBack = { overlay = null },
                    onOpenSettings = { overlay = CosOverlay.Settings },
                )
                CosOverlay.Settings -> SettingsScreen(
                    container = container,
                    onBack = { overlay = CosOverlay.Profile },
                )
                CosOverlay.Calendar -> CalendarScreen(
                    container = container,
                    onBack = { overlay = null },
                )
                null -> Unit
            }
        }
    }
}

/** The full-screen overlays reachable from the Now header (avatar → profile, icon → calendar). */
private enum class CosOverlay { Profile, Settings, Calendar }

@Composable
private fun ListeningOverlay(
    partial: String,
    inSession: Boolean = false,
    amplitude: Float = 0f,
    onDismiss: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Base.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ListeningOrb(amplitude = amplitude, modifier = Modifier.size(240.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                text = partial.ifBlank { if (inSession) "Hands-free" else "Listening…" },
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (inSession) "tap anywhere to end" else "tap anywhere to cancel",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.InkFaint,
            )
        }
    }
}
