# Architecture

The app is the §4 layer stack, assembled top-to-bottom in one hand-wired
[`AppContainer`](../app/src/main/java/com/chiefofstaff/AppContainer.kt). One user, one process — a
manual container keeps the whole graph readable in a single file, no annotation processor.

## Package map

| Package | Responsibility | Spec |
|---|---|---|
| `core` | `Clock` (injectable time — the prediction ledger depends on it), `AppLog` | — |
| `data` | Room entities, DAOs, on-device SQLite DB, `LifeRepository` façade | §7 |
| `llm` | Task defs, context assembler, prompt registry, provider adapters, router, validator, redaction, deterministic fallback | §6 |
| `domain` | Commitment state machine, prediction ledger, rule engine, plan generator, anticipation, consistency | ACC/DIR |
| `capture` | Capture manager, extraction pipeline, fact writer, speech, tile, calendar sync | CAP/MEM |
| `intervention` | Notifications + budget, morning brief + TTS, evening close, alarms, receivers, foreground service, workers | INT/REV |
| `system` | Backup/export, silent-failure detector, cost tracker, provider config, seed | SYS |
| `ui` | Theme + neumorphic components, four screens, view-models, `CosApp` | UX |

## Memory (§7)

`LifeRepository` fronts a plain Room/SQLite database stored in the app's private internal storage
(`/data/data/<pkg>/databases/cos.db`), which Android sandboxes to this app. Single user, single
device — local storage is the whole persistence story; the nightly JSON export (SYS-10/11) is the
backup against device loss. Captures are **immutable**
and FTS4-indexed on write; typed facts (Commitment, WaitingOn, Goal, Project, Rule, Person, …) sit
alongside and reference the source capture. A bad parse is always recoverable (P5, P12).

## LLM orchestration (§6) — four components

1. **Task definitions** ([`TaskDef.kt`](../app/src/main/java/com/chiefofstaff/llm/TaskDef.kt)) — the
   ~18 declared units of work, each with model tier, context recipe, prompt ref, schema, retry/cache
   policy. Adding capability = declaring a Task, never an agent loop (§6.1).
2. **Context assembler** — ordered recipe blocks with **hard per-block token budgets**, compact
   key-value rendering, recency/salience ranking, oldest truncated first (§6.3).
3. **Prompt registry** — versioned JSON on disk (`filesDir/prompts`, seeded from assets), never
   compiled into the APK so phrasing can be tuned without a rebuild (§6.4).
4. **Provider adapters** at capability level — Claude, OpenAI-compatible (GPT/Grok), offline stub —
   behind a **Router** (tier/availability/cost/cache, secondary failover) and a **Validator**
   (schema-check + one repair, else queue for nightly re-parse). Redaction runs before any egress.

**Degradation is the whole point (P11).** If everything remote is down, `Fallback` produces the brief
and the plan from local structured data. The 06:00 brief always fires.

## Accountability & direction

- **State machine** ([`CommitmentStateMachine`](../app/src/main/java/com/chiefofstaff/domain/CommitmentStateMachine.kt))
  is the only place transitions happen, so "every commitment gets a verdict" (P4) is structural. A
  third deferral escalates; 45-day dormancy auto-archives (RES-01).
- **Prediction ledger** writes predictions at plan time and resolves them with a signed delta at the
  close — the mechanism behind "self-aware" (§1.3) and the accuracy report (REV-09).
- **Consistency** is a rolling 30-day *fact*, never a streak (ACC-10). There is nothing to break.

## Intervention

Everything posts through `NotificationBudget` — the 3/day discretionary cap (INT-04) can't be
bypassed; rituals are essential and exempt; DND (INT-07) and quiet mode (INT-08) are respected.
`RitualScheduler` sets exact alarms (`setExactAndAllowWhileIdle`); a `RitualAlarmReceiver` hands work
to WorkManager and re-arms; `BootReceiver` re-arms after reboot; a foreground service (SYS-08) holds a
live process against OEM killing (§16.1); `SilentFailureDetector` turns a silent miss into a visible
one (SYS-12).

## Background timeline

```
02:00  NightlyBatchWorker   extract backlog · anticipation scan · auto-archive · calendar · backup
06:00  MorningBriefWorker   LLM brief or deterministic fallback, spoken + one essential notification
:15    CalendarSyncWorker   idempotent 14-day calendar read (periodic)
21:00  EveningCloseWorker   announce the card stack
ad hoc CommitmentDueWorker  per-commitment exact alarm → one-tap verdict notification
```

## Testing note

This slice is written to compile in Android Studio against the Android SDK; it was authored in an
environment without the SDK, so `./gradlew assembleDebug` has not been run here. The code is kept
compile-clean by review. First things to wire when validating on-device: a real prompt set beyond the
7 bundled tasks, provider API keys, and the OAuth decision for calendar (§16.4).
