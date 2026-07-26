package com.chiefofstaff.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.chiefofstaff.system.Permissions
import com.chiefofstaff.system.UserProfile
import com.chiefofstaff.ui.components.CosTextField
import com.chiefofstaff.ui.components.GhostButton
import com.chiefofstaff.ui.components.NeuButton
import com.chiefofstaff.ui.components.NeuButtonNeutral
import com.chiefofstaff.ui.components.NeuCard
import com.chiefofstaff.ui.components.SectionLabel
import com.chiefofstaff.ui.theme.AvatarGradient
import com.chiefofstaff.ui.theme.ChiefOfStaffTheme
import com.chiefofstaff.ui.theme.Palette

private enum class Step { PROFILE, PERMISSIONS }

/**
 * First-run onboarding. A warm, one-question-at-a-time conversation that actually gets to know the
 * person — who they are, and how they want to be worked with — then the permission step. Nothing is
 * requested until the user opts in, everything stays on the device, and any step can be skipped.
 */
@Composable
fun OnboardingScreen(onEnter: () -> Unit) {
    ChiefOfStaffTheme {
        var step by remember { mutableStateOf(Step.PROFILE) }
        when (step) {
            Step.PROFILE -> ConversationalProfile(onDone = { step = Step.PERMISSIONS })
            Step.PERMISSIONS -> PermissionStep(onEnter = onEnter)
        }
    }
}

// --- The conversation --------------------------------------------------------------------------

private class Opt(val value: String, val label: String, val emoji: String? = null)

private sealed interface Q {
    val key: String
    val coach: String
}

/** A single- or multi-select question rendered as tappable option cards / chips. */
private class ChoiceQ(
    override val key: String,
    override val coach: String,
    val options: List<Opt>,
    val multi: Boolean = false,
) : Q

/** A short free-text question. */
private class TextQ(
    override val key: String,
    override val coach: String,
    val placeholder: String,
    val optional: Boolean = true,
) : Q

private val QUESTIONS: List<Q> = listOf(
    TextQ("name", "Hi — I'm going to be your chief of staff. First things first: what should I call you?", "First name", optional = false),
    ChoiceQ("pronouns", "Lovely to meet you, {name}. How should I refer to you?", listOf(
        Opt("she", "She / her"), Opt("he", "He / him"), Opt("they", "They / them"), Opt("", "Rather not say"),
    )),
    ChoiceQ("focus", "What do you want to stay on top of? Pick as many as fit.", listOf(
        Opt("HEALTH", "Health", "🫀"), Opt("WORK", "Work", "💼"), Opt("MONEY", "Money", "💰"),
        Opt("PEOPLE", "People", "💬"), Opt("HOME", "Home", "🏠"), Opt("LEARNING", "Learning", "📚"),
        Opt("TRAVEL", "Travel", "✈️"), Opt("PROJECTS", "Projects", "🛠️"),
    ), multi = true),
    ChoiceQ("goal", "What would make the biggest difference for you right now?", listOf(
        Opt("drop", "Stop dropping the ball", "🎯"),
        Opt("consistent", "Be more consistent", "🔁"),
        Opt("people", "Make time for people", "💛"),
        Opt("overwhelm", "Get out of overwhelm", "🌊"),
        Opt("clarity", "Think more clearly", "🧠"),
    )),
    ChoiceQ("chronotype", "When are you at your sharpest?", listOf(
        Opt("early", "Early mornings", "🌅"), Opt("midday", "Midday", "☀️"),
        Opt("evening", "Evenings", "🌙"), Opt("varies", "It really varies", "🎲"),
    )),
    ChoiceQ("nudge", "When you commit to something, how should I show up?", listOf(
        Opt("gentle", "Nudge me gently", "🌿"),
        Opt("firm", "Hold me to it", "💪"),
        Opt("minimal", "Mostly stay out of the way", "🤫"),
    )),
    ChoiceQ("motivation", "And when you're off track, what helps more?", listOf(
        Opt("encourage", "A kind word", "🤗"),
        Opt("challenge", "A straight challenge", "🎯"),
    )),
    ChoiceQ("overwhelm", "On a heavy day, what do you want to see?", listOf(
        Opt("one", "Just the next thing", "☝️"),
        Opt("all", "Everything, so I can choose", "🗂️"),
    )),
    ChoiceQ("wellbeing", "Should I check in on how you're doing — not only what you're doing?", listOf(
        Opt("yes", "Yes, please", "💜"),
        Opt("light", "Keep it light", "🍃"),
    )),
    ChoiceQ("brief", "When should your morning brief land?", listOf(
        Opt("6", "6:00", "🌄"), Opt("7", "7:00", "☕"), Opt("8", "8:00", "🍳"), Opt("none", "Skip the brief", "🚫"),
    )),
    TextQ("goodday", "Last one, {name}. In a sentence — what does a good day look like for you?", "e.g. focused morning, dinner with family", optional = true),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConversationalProfile(onDone: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { UserProfile(context) }
    val total = QUESTIONS.size
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateMapOf<String, Any>() }
    val name = (answers["name"] as? String).orEmpty()

    // In-flow back steps to the previous question; at the first one the system default applies.
    BackHandler(enabled = index > 0) { index-- }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        TopProgress(step = index.coerceAtMost(total), total = total, onBack = { if (index > 0) index-- })
        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState >= initialState
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(260)) { w -> dir * w } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { w -> -dir * w } + fadeOut(tween(200)))
            },
            label = "onboarding-step",
        ) { i ->
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (i < total) {
                    val q = QUESTIONS[i]
                    CoachBubble(text = q.coach.replace("{name}", name.ifBlank { "there" }))
                    Spacer(Modifier.height(22.dp))
                    when (q) {
                        is ChoiceQ -> if (q.multi) {
                            MultiChoice(q, answers) { index++ }
                        } else {
                            SingleChoice(q) { value -> answers[q.key] = value; index++ }
                        }
                        is TextQ -> TextQuestion(q, answers) { index++ }
                    }
                } else {
                    Summary(answers) {
                        persist(profile, answers)
                        onDone()
                    }
                }
            }
        }
    }
}

// --- Question renderers -------------------------------------------------------------------------

@Composable
private fun SingleChoice(q: ChoiceQ, onPick: (String) -> Unit) {
    Column {
        q.options.forEach { opt ->
            OptionCard(opt.label, opt.emoji, selected = false, onClick = { onPick(opt.value) })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiChoice(q: ChoiceQ, answers: MutableMap<String, Any>, onContinue: () -> Unit) {
    @Suppress("UNCHECKED_CAST")
    val current = (answers[q.key] as? Set<String>) ?: emptySet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        q.options.forEach { opt ->
            val on = opt.value in current
            Box(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Palette.Accent else Palette.SurfaceSunken)
                    .clickable {
                        answers[q.key] = if (on) current - opt.value else current + opt.value
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = (opt.emoji?.let { "$it  " } ?: "") + opt.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else Palette.InkMuted,
                )
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    NeuButton("Continue", onContinue, Modifier.fillMaxWidth())
}

@Composable
private fun TextQuestion(q: TextQ, answers: MutableMap<String, Any>, onNext: () -> Unit) {
    val value = (answers[q.key] as? String).orEmpty()
    Column {
        CosTextField(
            value = value,
            onValueChange = { answers[q.key] = it },
            placeholder = q.placeholder,
            singleLine = q.key == "name",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        val canContinue = q.optional || value.isNotBlank()
        if (canContinue) {
            NeuButton("Continue", onNext, Modifier.fillMaxWidth())
        } else {
            NeuButtonNeutral("Continue", {}, Modifier.fillMaxWidth())
        }
        if (q.optional) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GhostButton("Skip", onClick = { answers[q.key] = value; onNext() })
            }
        }
    }
}

@Composable
private fun OptionCard(label: String, emoji: String?, selected: Boolean, onClick: () -> Unit) {
    NeuCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) Palette.Accent else Palette.Ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// --- Coach, progress, summary -------------------------------------------------------------------

/** A little assistant avatar (replaces the old meaningless dot) speaking the prompt in a bubble. */
@Composable
private fun CoachBubble(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        CoachAvatar(size = 44)
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                .background(Palette.Surface)
                .padding(16.dp),
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
        }
    }
}

@Composable
private fun CoachAvatar(size: Int) {
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(AvatarGradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
private fun TopProgress(step: Int, total: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            if (step > 0) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Palette.InkMuted, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.SurfaceSunken),
        ) {
            val fraction = ((step + 1).toFloat() / (total + 1).toFloat()).coerceIn(0.05f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Accent),
            )
        }
    }
}

@Composable
private fun Summary(answers: Map<String, Any>, onBegin: () -> Unit) {
    val name = (answers["name"] as? String).orEmpty().ifBlank { "there" }
    Column {
        CoachBubble(text = "That's everything, $name. Here's what I'll keep in mind — and I'll keep learning as we go.")
        Spacer(Modifier.height(20.dp))
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                summaryLine(answers, "focus", "Focused on")
                summaryLine(answers, "chronotype", "Sharpest")
                summaryLine(answers, "nudge", "Nudges")
                summaryLine(answers, "motivation", "When off track")
                summaryLine(answers, "wellbeing", "Check-ins")
            }
        }
        Spacer(Modifier.height(24.dp))
        NeuButton("Let's begin", onBegin, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun summaryLine(answers: Map<String, Any>, key: String, label: String) {
    val value = when (val a = answers[key]) {
        is Set<*> -> a.joinToString(", ") { readableLabel(key, it.toString()) }
        is String -> readableLabel(key, a)
        else -> ""
    }
    if (value.isBlank()) return
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label:  ", style = MaterialTheme.typography.bodyMedium, color = Palette.InkFaint)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
    }
}

/** Human-readable label for a stored answer value, for the summary card. */
private fun readableLabel(key: String, value: String): String = QUESTIONS
    .filterIsInstance<ChoiceQ>()
    .firstOrNull { it.key == key }
    ?.options?.firstOrNull { it.value == value }
    ?.label
    ?: value.replaceFirstChar { it.uppercase() }

/** Map the collected answers onto the profile store. */
private fun persist(profile: UserProfile, answers: Map<String, Any>) {
    fun s(key: String) = (answers[key] as? String).orEmpty()
    @Suppress("UNCHECKED_CAST")
    val focus = (answers["focus"] as? Set<String>) ?: emptySet()

    profile.name = s("name")
    profile.pronouns = s("pronouns")
    profile.focusAreas = focus
    profile.primaryGoal = s("goal")
    profile.chronotype = s("chronotype")
    profile.nudgeStyle = s("nudge")
    profile.motivation = s("motivation")
    profile.overwhelmStyle = s("overwhelm")
    profile.wellbeingCheckins = s("wellbeing") != "light"
    profile.briefHour = s("brief")
    profile.goodDay = s("goodday")
    profile.intention = s("goodday")
}

// --- Step 2: permissions -----------------------------------------------------------------------

/** One permission the onboarding walks the user through. */
private data class PermItem(
    val title: String,
    val why: String,
    val icon: ImageVector,
    val granted: Boolean,
    val open: () -> Unit,
)

/**
 * Requests the runtime permissions and — one at a time — drops the user on the exact Settings switch
 * for each special grant (notification access, usage access, exact alarms, battery). Returning from
 * any panel re-checks state, so a granted item flips to a check without a restart.
 */
@Composable
private fun PermissionStep(onEnter: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var consented by remember { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { tick++ }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val runtimeGranted = remember(tick) { Permissions.allRuntimeGranted(context) }
    val special = remember(tick) {
        listOf(
            PermItem(
                "Notification access", "Read commitments out of your message notifications.",
                Icons.Filled.NotificationsActive, Permissions.notificationListenerEnabled(context),
            ) { context.startActivity(Permissions.notificationListenerSettings()) },
            PermItem(
                "Usage access", "Notice which apps pull your attention, for the weekly read.",
                Icons.Filled.Insights, Permissions.usageAccessGranted(context),
            ) { context.startActivity(Permissions.usageAccessSettings()) },
            PermItem(
                "Exact alarms", "Fire the morning brief and reminders on the minute.",
                Icons.Filled.Alarm, Permissions.exactAlarmAllowed(context),
            ) { context.startActivity(Permissions.exactAlarmSettings(context)) },
            PermItem(
                "Unrestricted battery", "Stay alive so the morning brief never silently misses.",
                Icons.Filled.BatteryStd, Permissions.batteryUnrestricted(context),
            ) { context.startActivity(Permissions.batterySettings(context)) },
        )
    }
    val allDone = runtimeGranted && special.all { it.granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachAvatar(size = 44)
            Spacer(Modifier.width(12.dp))
            Text("A few permissions", style = MaterialTheme.typography.headlineMedium, color = Palette.Ink)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "I live on your phone — nothing leaves the device without being assembled and redacted first. " +
                "To capture and remind you reliably I need a few permissions. You grant each one yourself.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted,
        )
        Spacer(Modifier.height(24.dp))

        if (!consented) {
            NeuCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Set up permissions now?", style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap Yes and I'll ask Android for the standard ones, then open the exact settings " +
                            "screen for each special toggle — you flip it, I bring you back.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.InkMuted,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            NeuButton(
                text = "Yes — set them up",
                onClick = {
                    consented = true
                    if (!runtimeGranted) runtimeLauncher.launch(Permissions.runtimeWanted())
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GhostButton(text = "Not now", onClick = onEnter)
            }
        } else {
            SectionLabel("STANDARD")
            Spacer(Modifier.height(10.dp))
            PermRow(
                PermItem(
                    "Mic · notifications · calendar · camera",
                    "Granted through the standard Android popup.",
                    Icons.Filled.Mic, runtimeGranted,
                ) { if (!runtimeGranted) runtimeLauncher.launch(Permissions.runtimeWanted()) },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("SPECIAL ACCESS — YOU FLIP THE SWITCH")
            Spacer(Modifier.height(10.dp))
            special.forEach { item ->
                PermRow(item)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
            NeuButton(
                text = if (allDone) "Enter" else "Continue anyway",
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!allDone) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "You can grant the rest later — the app works without them.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.InkFaint,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PermRow(item: PermItem) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = Palette.InkMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
                Text(item.why, style = MaterialTheme.typography.bodySmall, color = Palette.InkMuted)
            }
            Spacer(Modifier.width(12.dp))
            if (item.granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = "Granted", tint = Palette.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("On", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                }
            } else {
                NeuButtonNeutral(text = "Open", onClick = item.open)
            }
        }
    }
}
