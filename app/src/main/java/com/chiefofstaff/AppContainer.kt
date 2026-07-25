package com.chiefofstaff

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.chiefofstaff.capture.CalendarSync
import com.chiefofstaff.capture.CaptureManager
import com.chiefofstaff.capture.FactExtractionPipeline
import com.chiefofstaff.capture.FactWriter
import com.chiefofstaff.capture.HealthConnectSync
import com.chiefofstaff.core.Clock
import com.chiefofstaff.core.SystemClock
import com.chiefofstaff.data.CoSDatabase
import com.chiefofstaff.data.LifeRepository
import com.chiefofstaff.domain.AnticipationEngine
import com.chiefofstaff.domain.CommitmentStateMachine
import com.chiefofstaff.domain.ConsistencyScore
import com.chiefofstaff.domain.PlanGenerator
import com.chiefofstaff.domain.PredictionLedger
import com.chiefofstaff.domain.ReductionEngine
import com.chiefofstaff.domain.RuleEngine
import com.chiefofstaff.intervention.EveningClose
import com.chiefofstaff.intervention.MorningBrief
import com.chiefofstaff.intervention.NotificationBudget
import com.chiefofstaff.intervention.Notifier
import com.chiefofstaff.intervention.RitualScheduler
import com.chiefofstaff.intervention.TtsSpeaker
import com.chiefofstaff.intervention.WeeklyAudit
import com.chiefofstaff.llm.ContextAssembler
import com.chiefofstaff.llm.ContextProviders
import com.chiefofstaff.llm.Fallback
import com.chiefofstaff.llm.LlmOrchestrator
import com.chiefofstaff.llm.LlmProvider
import com.chiefofstaff.llm.PromptRegistry
import com.chiefofstaff.llm.Router
import com.chiefofstaff.llm.Validator
import com.chiefofstaff.llm.adapters.ClaudeAdapter
import com.chiefofstaff.llm.adapters.OfflineStubProvider
import com.chiefofstaff.llm.adapters.OpenAiCompatAdapter
import com.chiefofstaff.system.Backup
import com.chiefofstaff.system.CostTracker
import com.chiefofstaff.system.ProviderConfig
import com.chiefofstaff.system.SilentFailureDetector
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph. One user, one process — a hand-wired container is simpler to read and
 * debug than an annotation processor, and it keeps the whole architecture visible in one file: the
 * §4 layers assembled top to bottom, memory at the base feeding direction, anticipation and
 * conversation, all of it fronted by the two rituals.
 *
 * Everything is lazy so app start is cheap; the SQLCipher DB and TTS engine only spin up on first
 * use. The single [appScope] owns background coroutines that outlive any one screen.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock: Clock = SystemClock()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- Data ---
    private val db: CoSDatabase by lazy { CoSDatabase.build(appContext) }
    val repo: LifeRepository by lazy { LifeRepository(db, clock) }

    // --- LLM orchestration ---
    private val http: HttpClient by lazy { HttpClient(Android) }
    private val providerConfig by lazy { ProviderConfig(appContext) }
    val costTracker = CostTracker()

    private val providers: List<LlmProvider> by lazy {
        buildList {
            providerConfig.claudeKey.takeIf { it.isNotBlank() }?.let { add(ClaudeAdapter(http, it)) }
            providerConfig.openAiKey.takeIf { it.isNotBlank() }?.let {
                add(OpenAiCompatAdapter(http, it, "https://api.openai.com/v1", "openai", "gpt-4o-mini", "gpt-4o"))
            }
            providerConfig.grokKey.takeIf { it.isNotBlank() }?.let {
                add(OpenAiCompatAdapter(http, it, "https://api.x.ai/v1", "grok", "grok-2-mini", "grok-2"))
            }
            add(OfflineStubProvider())   // always last; keeps the app whole with no keys (P11)
        }
    }

    private val assembler: ContextAssembler by lazy { ContextAssembler(ContextProviders.all(repo, clock)) }
    private val prompts: PromptRegistry by lazy { PromptRegistry.create(appContext) }
    private val router: Router by lazy { Router(providers) }
    private val validator = Validator()

    val orchestrator: LlmOrchestrator by lazy {
        LlmOrchestrator(assembler, prompts, router, validator, clock) { task, usage, provider ->
            costTracker.record(task, usage, provider)
        }
    }
    val fallback: Fallback by lazy { Fallback(repo, clock) }

    // --- Domain ---
    val ledger: PredictionLedger by lazy { PredictionLedger(repo, clock) }
    val stateMachine: CommitmentStateMachine by lazy { CommitmentStateMachine(repo, clock, ledger) }
    val ruleEngine: RuleEngine by lazy { RuleEngine(repo, clock) }
    val planGenerator: PlanGenerator by lazy { PlanGenerator(repo, clock, orchestrator, fallback, ruleEngine, ledger) }
    val anticipationEngine: AnticipationEngine by lazy { AnticipationEngine(repo, clock) }
    val consistencyScore: ConsistencyScore by lazy { ConsistencyScore(repo, clock) }
    val reductionEngine: ReductionEngine by lazy { ReductionEngine(repo, clock, stateMachine) }

    // --- Capture ---
    private val factWriter: FactWriter by lazy { FactWriter(repo, clock) }
    val extractionPipeline: FactExtractionPipeline by lazy { FactExtractionPipeline(repo, orchestrator, factWriter, clock) }
    val captureManager: CaptureManager by lazy { CaptureManager(repo, extractionPipeline, appScope, ::isOnline) }
    val calendarSync: CalendarSync by lazy { CalendarSync(appContext, repo, clock) }
    val healthConnectSync: HealthConnectSync by lazy { HealthConnectSync(appContext, repo, clock) }

    // --- Intervention ---
    val notificationBudget: NotificationBudget by lazy { NotificationBudget(appContext, repo, clock) }
    val notifier: Notifier by lazy { Notifier(appContext, notificationBudget) }
    private val tts: TtsSpeaker by lazy { TtsSpeaker(appContext) }
    val morningBrief: MorningBrief by lazy { MorningBrief(repo, clock, orchestrator, fallback, tts, notifier, calendarSync) }
    val eveningClose: EveningClose by lazy { EveningClose(repo, clock, stateMachine, orchestrator, notifier) }
    val weeklyAudit: WeeklyAudit by lazy { WeeklyAudit(repo, clock, orchestrator, consistencyScore, ledger, notifier) }
    val ritualScheduler: RitualScheduler by lazy { RitualScheduler(appContext, clock) }

    // --- System ---
    val backup: Backup by lazy { Backup(appContext, repo, clock) }
    val silentFailureDetector: SilentFailureDetector by lazy { SilentFailureDetector(repo, clock, notifier) }

    /**
     * RES-03 — days since the app was last opened, computed once at startup, then the marker is
     * advanced to now. Drives the gentle re-entry flow: after an absence the app welcomes you back
     * and shows only today, never a backlog dump.
     */
    val daysAway: Int by lazy {
        val prefs = appContext.getSharedPreferences("cos_flags", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_opened", 0L)
        val now = clock.epochMillis()
        val days = if (last == 0L) 0 else ((now - last) / 86_400_000L).toInt()
        prefs.edit().putLong("last_opened", now).apply()
        days
    }

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
