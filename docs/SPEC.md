# Personal AI Assistant — Build Specification

**Version:** 1.0 (build-ready)
**Date:** 25 July 2026
**Supersedes:** v0.2
**Platform:** Android, sideloaded, single user
**Status:** Pre-build. Manual validation week 26 Jul – 2 Aug 2026.

---

# PART I — INTENT

## 1. Purpose

A private chief-of-staff that runs on one Android phone, for one person, and takes responsibility for the operation of an entire life — work, health, money, people, admin, and long-term direction.

It must do four things a to-do app cannot:

1. **Know** — hold a durable, queryable model of your life, built mostly from data you never entered
2. **Plan** — decide what should happen today, and what should not
3. **Verify** — close the loop on every commitment, and remember whether it happened
4. **Discuss** — think through problems with you, with full context, and push back

Point four is the difference between a tracker and a secretary. Points one and three are what make point four worth having.

### 1.1 The problem

A demanding IT job has crowded out physical health, food quality, and personal structure. The failure is not lack of knowledge. It is that nothing closes the loop between intention and completion, nothing holds the full picture, and nothing pushes back.

### 1.2 Success criteria — evaluated 20 September 2026 (day 60)

| Metric | Target |
|---|---|
| Days opened, of last 14 | ≥ 12 |
| Evening close completed, of last 14 | ≥ 10 |
| Commitments reaching a verdict | ≥ 85% |
| Morning brief fires on schedule | ≥ 95% of days |
| Manual typing required per day | ≤ 5 minutes |
| Conversations initiated by user per week | ≥ 3 |
| Anticipation prompts rated useful | ≥ 60% |

Missing these is a result, not a failure. The build stops or redesigns. No sunk-cost continuation.

### 1.3 "Self-aware", defined so it can be built

> The system records what it asked, what it predicted, and what actually happened — and can report on its own accuracy.

A **prediction ledger** backs this. When it says "you'll finish this by 4pm," that is written down. When the evening close shows it didn't happen, the miss is recorded. Over weeks this yields a system that knows it is bad at estimating your Tuesday evenings and stops planning them optimistically. Nothing beyond this is a build target.

---

## 2. Design principles

Laws, not preferences. Every feature request is tested against these.

**P1 — It must know things you never told it.** Any feature depending on the user remembering to log something is a liability. Capture preference order: sensor > notification scrape > photo > voice > typing > form.

**P2 — Two rituals carry the product.** The morning brief and the evening close. Everything else exists to make those better.

**P3 — Notification budget is hard-capped.** 3/day in V0, 5/day in V1. Getting muted is unrecoverable.

**P4 — Every commitment gets a verdict.** Done, skipped, deferred, or explicitly dropped. Silence is never an outcome.

**P5 — Captures are immutable.** Raw input is never edited. Derived facts sit alongside. Bad parses stay recoverable.

**P6 — One tap, or one word.** Confirming, deferring, or logging costs a single gesture. Two taps means it won't happen at 11pm.

**P7 — Three things, not thirty.** The daily plan proposes 3–6 items.

**P8 — Never publish.** Sideload only. Removes Play Store policy limits, DPDP obligations, and rejections for the restricted Android APIs this design depends on.

**P9 — Features are capabilities, not buttons.** A capability earns permanent screen real estate only if used daily. Everything else is reachable by voice or search. This is how ~150 features fit behind 4 screens.

**P10 — The assistant proposes its own reduction.** It must periodically suggest dropping goals, archiving commitments, and muting domains. A system that only ever adds becomes a burden and gets abandoned.

**P11 — Degrade, never disappear.** An LLM or network failure must never break a ritual. Reliability is the entire value proposition.

**P12 — Wrong is fine, unfixable is not.** Every mis-parse must be correctable in one gesture, from where you see it.

---

## 3. The minimalism problem

The stated requirement is contradictory on its face: comprehensive capability, minimal interface. It resolves cleanly once you stop thinking of features as screens.

### 3.1 The resolution

- ~150 capabilities exist
- 4 screens exist
- The primary interface is **voice and a single card**
- Navigation depth never exceeds 2
- The home screen displays at most 6 objects, ever
- Capabilities surface **progressively** — trends appear when there is data to trend, the people layer appears once people exist, long-horizon planning appears in month two. Week one shows almost nothing.

### 3.2 The four screens

**1. Now** — the home screen. Today's 3–6 items, one line of context above them, one large hold-to-talk button. A single status line (mode, streak-free consistency %, next event). Nothing else. No tabs, no menu, no badges.

**2. Talk** — the conversation surface. Full-height thread, voice or text, complete memory access. This is where planning, thinking and discussion happen. Empty by default; no suggested prompts cluttering it.

**3. Close** — the evening card stack. Appears automatically at ritual time; otherwise a single small entry point from Now. One card at a time, swipe to verdict.

**4. Look** — everything else. A search field at the top and four collapsed sections: Timeline, Trends, Domains, People. Collapsed by default. This screen is where 100 of the 150 features live, and it is visited perhaps twice a week.

### 3.3 Anti-burden mechanisms (mandatory, not optional)

| Mechanism | Behaviour |
|---|---|
| Auto-archive | Commitments untouched for 45 days are archived with a single notification listing them |
| Domain bankruptcy | One action clears a whole life domain's backlog without shame or reconstruction |
| Quiet mode | 24h–7d suppression of all proactive output; captures still flow |
| Minimum viable day | On low-energy or high-load days, the plan reduces to one item |
| Reduction proposals | Monthly: "these 6 goals have had no activity in 8 weeks. Drop them?" |
| No red badges, no counts | Never display a number of pending items on an icon |
| No guilt language | Missed items are reported neutrally. Never "you failed to…" |

The retention risk is not that the app does too little. It is that it accumulates.

---

# PART II — ARCHITECTURE

## 4. System layers

```
CAPTURE       voice, calendar, notifications, sensors, photos, email
    ↓
MEMORY        immutable captures → typed facts → life graph
    ↓
    ├──→ DIRECTION      plan, rank, what-now, what-not
    ├──→ ANTICIPATION   look-ahead, risk detection, expiry watch
    └──→ CONVERSATION   discuss, think, decide, recall
                ↓
        INTERVENTION    brief, alarms, check-ins, nudges
                ↓
        REVIEW          close, audit, prediction ledger
                └────→ refines MEMORY
```

Direction, Anticipation and Conversation are peers reading the same memory. The return arrow from Review is what makes it a secretary rather than a nagging alarm clock.

### 4.1 Processing model

- **On-device first.** All storage local and encrypted. LLM calls are the only network egress.
- **Two-tier routing.** Cheap model for extraction, classification, entity resolution (dozens of calls/day). Flagship for planning, briefs, conversation, reasoning (2–6 calls/day). Roughly 5× cost saving.
- **Deterministic before probabilistic.** Regex, date parsers, and rules run first. Never spend a token on what `if` can answer.
- **Batch at 02:00.** Re-parsing, entity resolution, trend computation, anticipation scan, backup.

---

## 5. Technology stack

| Concern | Choice | Rationale |
|---|---|---|
| Language | Kotlin | Native access to alarms, notification listener, Health Connect, foreground services. Cross-platform frameworks cannot reach these cleanly. |
| UI | Jetpack Compose | Fast iteration, one developer |
| Database | Room (SQLite) | Structured facts and relations |
| Search | SQLite FTS5 | Full-text over captures and notes |
| Encryption | SQLCipher | Database at rest |
| Background | WorkManager + Foreground Service | Survives OEM process killing |
| Alarms | AlarmManager `setExactAndAllowWhileIdle` | Exact firing in Doze |
| STT | Hybrid: on-device for short commands, API for long-form | On-device degrades badly on 90-second unstructured accented speech |
| TTS | Android TTS | Spoken brief |
| LLM | Provider-abstracted; any of Grok / Claude / GPT | See §6 |
| Health | Health Connect | Steps, sleep, weight, HR from any wearable |
| Calendar | Google Calendar API | Read in V0, write in V2 |
| Email | Gmail API, readonly | V2, extraction only, never sends |
| Backup | Nightly encrypted export to Drive | One phone is a single point of failure |
| Server | None in V0 | Optional ₹500/mo VPS in V2 if jobs must fire with phone off |

---

## 6. LLM orchestration

Provider-agnostic, with application logic as configuration rather than compiled code. Four components, no external framework, ~1,500 lines of Kotlin.

### 6.1 Two things deliberately not built

| Rejected | Reason |
|---|---|
| Vector DB / RAG | ~4,000 structured facts, one user. Relevance is always deterministically known (today's calendar, open commitments, active rules, this person). SQL beats similarity search on accuracy, latency and cost at this scale. FTS5 covers free-form historical queries. Revisit only if evidence demands it. |
| Autonomous agent loop | Unbounded latency, cost and battery on a phone, for flexibility not needed. ~18 fixed pipelines cover the app. Exactly one task (open-ended Q&A) gets a single tool-calling step. |

### 6.2 Component 1 — Task definitions

A Task is a declared unit of LLM work. Approximately 18 exist:

`extract_facts` · `resolve_entity` · `generate_plan` · `morning_brief` · `evening_parse` · `match_commitments` · `weekly_audit` · `anticipate` · `decision_support` · `converse` · `answer_question` · `summarise_meeting` · `estimate_meal` · `detect_conflict` · `propose_reduction` · `draft_message` · `explain_trend` · `bootstrap_interview`

Each declares: id, model tier, context recipe, prompt ref, output schema, retry policy, cache policy, token ceiling.

### 6.3 Component 2 — Context assembler

Each task names a recipe: ordered blocks with individual token budgets.

```
generate_plan:
  today_calendar       400
  open_commitments     800
  active_rules         200
  current_state        100
  recent_observations  300
  yesterday_verdicts   200
  prediction_accuracy  100
  active_goals         200
```

Each block is a `ContextProvider` running SQL and rendering compact text. Rules:
- Structured queries first; keyword retrieval only for free-form history
- Rank by recency and salience, not similarity
- Hard per-block budget, oldest truncated first
- Compact key-value lines, not JSON — JSON punctuation wastes ~30% of budget
- Context curation determines output quality far more than model choice

### 6.4 Component 3 — Prompt registry

One JSON file per task version in app storage, synced from an external folder. Contains system prompt, user template, output schema, 2–3 few-shot examples, model tier, temperature. Never compiled into the APK — needing a rebuild to fix a phrasing issue means you will stop tuning.

Every capture records the `prompt_version` that parsed it, enabling version comparison on identical historical input.

### 6.5 Component 4 — Provider adapters

Abstraction is at capability level, not HTTP level.

| Capability | Variation | Handling |
|---|---|---|
| Structured output | Native schema / JSON mode / prompted only | Per-provider strategy |
| Tool calling | Differing formats | Normalised to internal spec |
| System prompt | Separate field vs first message | Adapter-level |
| Prompt caching | Supported or not | Router prefers cache-capable for repeated prefixes |
| Context ceiling | Varies widely | Assembler reads from capability flags |
| Rate limits | Provider-specific | Router backoff |

**Router** selects by tier, availability, cost ceiling and cache capability, with a secondary provider on failure.

**Validator** schema-checks every response, attempts one repair, then queues for nightly re-parse. An invalid response never crashes a ritual.

### 6.6 Failure and degradation

| Failure | Behaviour |
|---|---|
| Network down | Captures queue. Deterministic fallback brief: calendar + open commitments by due date, no LLM. The 06:00 brief always fires. |
| Provider down | Router falls to secondary |
| Invalid JSON | One repair, then nightly re-parse queue |
| Rate limited | Backoff; non-urgent tasks deferred to 02:00 batch |
| Transcription failure | Audio retained, retried in batch |

### 6.7 Evaluation harness

Provider-agnosticism is theatre without this. 25 real captured inputs with hand-checked expected outputs, runnable against any task, prompt version or provider. Enables switching models on evidence, catching prompt regressions before they corrupt a week of facts, and comparing cheap vs flagship on the tasks that run most. One day of work in V1.

### 6.8 Privacy boundary

Only assembled context leaves the device — never the raw capture log, never the database. Redaction pass before egress on account numbers, card numbers, and anything tagged `never_send`. Per-domain egress control: a domain (e.g. money) can be marked local-only and handled by deterministic rules alone.

### 6.9 Running cost

| Item | Monthly |
|---|---|
| Extraction tier (~40 calls/day) | ₹60–120 |
| Reasoning tier (2–6 calls/day) | ₹100–220 |
| Conversation (variable) | ₹50–200 |
| Transcription | ₹150–300 |
| **Total** | **₹360–840** |

---

# PART III — THE LIFE MODEL

## 7. Data model

### 7.1 Capture (immutable)

`id` · `source` (voice/text/calendar/notification/sensor/photo/email) · `created_at` · `raw_content` · `media_path` · `parse_version` · `confidence`

### 7.2 Fact types

| Type | Key fields | Purpose |
|---|---|---|
| `Value` | statement, rank | Root of the tree. 5–7 statements. What you're optimising for. |
| `Goal` | title, horizon (year/quarter/month), domain, metric, target, status, parent_value | The layer that makes prioritisation non-arbitrary |
| `Project` | title, outcome, deadline, domain, parent_goal, status | Container for multi-step work. Commitments alone cannot represent "renovate the kitchen". |
| `Commitment` | what, due_at, energy_cost, blocked_by, domain, parent_project, state, deferral_count | The atomic unit of doing |
| `WaitingOn` | what, who, promised_at, expected_by, chase_count, state | What others owe you. Half of working life. |
| `Event` | title, start, end, location, attendees, source | Time constraints |
| `Interaction` | person, channel, when, summary, commitments_made, commitments_received | What actually happened with a person |
| `Observation` | metric, value, unit, observed_at, source | Health and behaviour data |
| `Person` | name, aliases, relationship, importance, last_contact, cadence_target | The people layer |
| `Decision` | question, chosen, rationale, expected_outcome, review_at, actual_outcome | Enables auditing your own judgment |
| `Rule` | statement, domain, enforcement (hard/soft), active_hours | Your constitution. The referee needs a rulebook. |
| `Reference` | type, label, value_encrypted, expires_at | Passport, policy numbers, blood group, emergency contacts. The "knows everything" layer. |
| `Note` | text, tags | Junk drawer. Keep it. |

### 7.3 State entities

| Entity | Fields |
|---|---|
| `DayState` | date, energy (1–5), mood, sleep_quality, illness_flag, load_score |
| `Mode` | current: normal / deep-work / travel / recovery / sick / weekend; affects notification budget and plan shape |
| `Session` | conversation thread id, started_at, context_snapshot, outcome_facts |
| `Prediction` | predicted_at, subject_id, prediction, confidence, actual, resolved_at, delta |
| `ReviewQueue` | capture_id, reason (low confidence / ambiguous / conflicting), resolved |

### 7.4 Commitment lifecycle

```
captured → scheduled → due → asked
                              ├→ done
                              ├→ skipped (reason optional)
                              ├→ deferred (count++) → scheduled
                              └→ [deferred ≥3] → escalated → dropped | recommitted
                                                                  ↓
                                        [untouched 45d] → auto-archived
```

Nothing exits without a verdict. P4 made concrete.

---

## 8. Bootstrap — solving the cold start

Without this, the assistant is useless for six weeks. With it, it is useful on day two.

**The life intake:** a guided voice interview, 40 minutes, run in 4 sittings of 10 minutes. The assistant asks; you talk; it populates the graph.

| Sitting | Populates |
|---|---|
| 1 — Shape of life | Work hours, commute, fixed obligations, household, current mode → `Rule`, `Event` |
| 2 — State of things | Health status, money situation, work pressures, what's broken → `Observation`, `Project`, `Goal` |
| 3 — People | Who matters, who you owe, who owes you, contact cadence → `Person`, `WaitingOn` |
| 4 — Direction | What you're optimising for, 12-month goals, non-negotiables → `Value`, `Goal`, `Rule` |

Re-runnable quarterly. Sitting 4 is re-run at every quarterly review.

**Reference vault** is populated opportunistically, not in the interview: whenever a document is photographed, its details are extracted and stored encrypted.

---

## 9. Accountability loop

### 9.1 Check-in points

| Time | Type | Duration | Content |
|---|---|---|---|
| 06:00 | Morning brief | 90 sec, spoken | Today's 3–6 items, calendar, one health note, one risk warning |
| Midday | Pulse (V1, conditional) | 15 sec | One question, only if a morning commitment is at risk |
| 21:00 | Evening close | 5 min | Verdict on each due item, free recap, tomorrow confirmed |
| Sun 19:00 | Weekly audit | 10 min | Planned vs actual, trends, one specific change |
| Quarterly | Direction review | 30 min | Goals re-set, intake sitting 4 re-run |

Ritual times are learned, not fixed — they adapt to travel, mode, and observed behaviour.

### 9.2 Verification interaction

Card stack, one item at a time. Swipe right = done, left = skipped, up = defer, down = drop. Voice equivalent: "done" / "skipped" / "tomorrow" / "drop it". Skipping asks one optional "why?", dismissible with one tap — reasons are the most valuable data in the system and must never be mandatory. Total budget: 5 minutes for a 6-item day.

---

## 10. Conversation & thinking partner

The layer that makes it a secretary rather than a tracker. Full memory access, multi-turn, voice or text.

### 10.1 Conversation modes

| Mode | Behaviour | Invoked by |
|---|---|---|
| Recall | Answers from your own history with citations to captures | "when did I…", "what did I say about…" |
| Plan | Works through scheduling and sequencing against real constraints | "help me plan this week" |
| Think | Open-ended reasoning with full context loaded | "I'm considering…" |
| Challenge | Deliberately argues the other side, surfaces contradictions with your stated rules and past decisions | "push back on this" |
| Decide | Structured decision: options, criteria, past analogues, writes a `Decision` record | "help me decide" |
| Draft | Writes messages, emails, agendas using your context and voice | "draft a reply to…" |
| Debrief | Post-event: what happened, what was committed, by whom | after a meeting |

### 10.2 Rules

- Session context is snapshotted so a conversation is reproducible
- Any conversation can emit facts — commitments, decisions, notes — with one confirmation tap. This is a major capture channel.
- Challenge mode is not optional politeness. If a plan contradicts a stated `Rule` or a past `Decision` outcome, it says so.
- Conversations are searchable and linked to the facts they produced

---

## 11. Anticipation engine

Runs in the 02:00 batch. Produces at most **one** anticipation item per day, delivered inside the morning brief. This is the highest-perceived-intelligence feature in the app and the easiest to ruin with volume.

| Scan | Detects |
|---|---|
| Horizon scan | Events in next 14 days with unprepared dependencies (flight without cab, meeting without prep) |
| Expiry watch | Passport, licence, insurance, subscriptions, medication refills, warranty |
| Streak-break risk | Pattern deviation: 3 late nights before an early commitment |
| Overload forecast | Next week's committed hours exceed available hours |
| Neglect detection | Domain, person, or goal with no activity beyond its expected cadence |
| Trend inflection | A metric that has changed direction and sustained it |
| Conflict prediction | Two commitments that will collide before they do |
| Waiting-on decay | Something you're owed that is now overdue and unchased |
| Financial calendar | Bills, EMIs, renewals, tax dates approaching |
| Decision review due | A `Decision` whose `review_at` has arrived |

---

# PART IV — CATALOGUE

## 12. Feature catalogue

**Phase key:** `V0` days 1–12 · `V1` weeks 3–6 · `V2` weeks 7–12 · `V3` conditional on usage data

### 12.1 Foundation (FDN)

| ID | Feature | Phase |
|---|---|---|
| FDN-01 | Life intake interview, 4 sittings | V0 |
| FDN-02 | Value statements (5–7, ranked) | V0 |
| FDN-03 | Goal hierarchy: year → quarter → month | V1 |
| FDN-04 | Rule declaration and editing by voice | V0 |
| FDN-05 | Domain activation — turn life areas on/off | V0 |
| FDN-06 | Quarterly re-intake | V2 |
| FDN-07 | Reference vault (encrypted, opportunistic capture) | V1 |
| FDN-08 | Emergency card — blood group, contacts, allergies, policies | V1 |

### 12.2 Capture (CAP)

| ID | Feature | Phase |
|---|---|---|
| CAP-01 | Hold-to-talk ramble, 90 sec, parsed into facts | V0 |
| CAP-02 | Quick Settings tile capture | V0 |
| CAP-03 | Lockscreen widget capture | V0 |
| CAP-04 | Text quick-add | V0 |
| CAP-05 | Calendar read sync, 15-min interval | V0 |
| CAP-06 | Notification listener — commitment extraction from messages | V1 |
| CAP-07 | Health Connect sync — steps, sleep, weight, HR | V1 |
| CAP-08 | Meal photo + one word → estimate | V1 |
| CAP-09 | Screen-time ingest | V1 |
| CAP-10 | Daily state tap — energy 1–5, one gesture | V1 |
| CAP-11 | Geofence context — home / office / gym | V2 |
| CAP-12 | Email scan, read-only | V2 |
| CAP-13 | Calendar write-back | V2 |
| CAP-14 | Call log → Interaction records | V2 |
| CAP-15 | Document photo → Reference extraction | V2 |
| CAP-16 | Bank/UPI notification → spend observation | V2 |
| CAP-17 | Meeting audio capture and summarise | V3 |
| CAP-18 | Share-sheet capture from any app | V1 |

### 12.3 Memory (MEM)

| ID | Feature | Phase |
|---|---|---|
| MEM-01 | Immutable capture log | V0 |
| MEM-02 | Fact extraction pipeline | V0 |
| MEM-03 | FTS5 full-text search | V0 |
| MEM-04 | Structured query by domain / date / person / state | V0 |
| MEM-05 | Review queue for low-confidence parses | V0 |
| MEM-06 | One-gesture correction from anywhere a fact appears | V0 |
| MEM-07 | Entity resolution — alias merging | V1 |
| MEM-08 | Open-loop detection | V1 |
| MEM-09 | Person graph with cadence tracking | V1 |
| MEM-10 | Timeline query | V1 |
| MEM-11 | Waiting-on register | V1 |
| MEM-12 | Interaction history per person | V2 |
| MEM-13 | Contradiction flagging | V2 |
| MEM-14 | Nightly re-parse with improved prompts | V2 |
| MEM-15 | Decision archive with outcomes | V2 |
| MEM-16 | Semantic retrieval — only if FTS demonstrably fails | V3 |

### 12.4 Accountability (ACC)

| ID | Feature | Phase |
|---|---|---|
| ACC-01 | Commitment state machine | V0 |
| ACC-02 | Evening close card stack | V0 |
| ACC-03 | One-word voice verdict | V0 |
| ACC-04 | Verdict from notification shade | V0 |
| ACC-05 | Prediction ledger | V0 |
| ACC-06 | Per-commitment exact alarm | V0 |
| ACC-07 | Skip-reason capture, optional | V1 |
| ACC-08 | Deferral counter and escalation | V1 |
| ACC-09 | Midday pulse, conditional | V1 |
| ACC-10 | Rolling 30-day consistency score (no streaks) | V1 |
| ACC-11 | Waiting-on chase prompts | V1 |
| ACC-12 | Sensor-contradiction flag on claimed completions | V2 |
| ACC-13 | Self-accuracy report | V2 |
| ACC-14 | Commitment renegotiation — bulk reschedule by voice | V2 |

### 12.5 Direction (DIR)

| ID | Feature | Phase |
|---|---|---|
| DIR-01 | Tomorrow's plan generated at 21:00 | V0 |
| DIR-02 | "What now?" — one tap, one answer | V0 |
| DIR-03 | Rule engine as planning constraint | V0 |
| DIR-04 | Minimum viable day — low-energy plan reduction | V0 |
| DIR-05 | Overcommit warning | V1 |
| DIR-06 | The "should not" list — decline, defer, drop | V1 |
| DIR-07 | Energy-aware scheduling from completion history | V1 |
| DIR-08 | Conflict detection | V1 |
| DIR-09 | Mode switching — work / travel / recovery / sick | V1 |
| DIR-10 | Goal-to-week decomposition | V2 |
| DIR-11 | Weekly horizon with drift detection | V2 |
| DIR-12 | Monthly and quarterly horizon | V2 |
| DIR-13 | Pre-meeting prep briefing | V2 |
| DIR-14 | Decision support with past analogues | V2 |
| DIR-15 | Time-blocking proposal against real calendar gaps | V2 |
| DIR-16 | Scenario projection — "at this pace, where am I in 3 months" | V3 |

### 12.6 Conversation (CNV)

| ID | Feature | Phase |
|---|---|---|
| CNV-01 | Conversation surface, text | V0 |
| CNV-02 | Full-memory context assembly for conversation | V0 |
| CNV-03 | Recall mode with citations to source captures | V0 |
| CNV-04 | Fact emission from conversation, one-tap confirm | V0 |
| CNV-05 | Voice conversation | V1 |
| CNV-06 | Plan mode | V1 |
| CNV-07 | Think mode | V1 |
| CNV-08 | Challenge mode — argues the other side, cites your rules | V1 |
| CNV-09 | Decide mode — writes a Decision record | V1 |
| CNV-10 | Draft mode — messages, emails, agendas | V2 |
| CNV-11 | Debrief mode — post-meeting extraction | V2 |
| CNV-12 | Session history, searchable and linked to emitted facts | V1 |
| CNV-13 | Continuous voice session, hands-free | V3 |

### 12.7 Anticipation (ANT)

| ID | Feature | Phase |
|---|---|---|
| ANT-01 | Nightly look-ahead scan | V1 |
| ANT-02 | One anticipation item per day, in the brief | V1 |
| ANT-03 | Expiry watch — documents, insurance, subscriptions | V1 |
| ANT-04 | Unprepared-dependency detection | V1 |
| ANT-05 | Overload forecast | V1 |
| ANT-06 | Neglect detection — domain, person, goal | V2 |
| ANT-07 | Streak-break risk warning | V2 |
| ANT-08 | Trend inflection alert | V2 |
| ANT-09 | Financial calendar look-ahead | V2 |
| ANT-10 | Decision review triggers | V2 |
| ANT-11 | Usefulness rating on each anticipation, tunes the scan | V1 |

### 12.8 Intervention (INT)

| ID | Feature | Phase |
|---|---|---|
| INT-01 | Morning brief, spoken, 90 sec | V0 |
| INT-02 | Exact alarms set by voice | V0 |
| INT-03 | Recurring routine alarms — gym, meals, sleep, medication | V0 |
| INT-04 | Notification budget enforcement in code | V0 |
| INT-05 | Separate notification channels, individually mutable | V0 |
| INT-06 | Home screen widget — today's items | V0 |
| INT-07 | Absolute DND respect | V0 |
| INT-08 | Quiet mode, 24h to 7d | V0 |
| INT-09 | Rule referee | V1 |
| INT-10 | Escalation tone shift on repeated deferral | V1 |
| INT-11 | Snooze with recorded consequence | V1 |
| INT-12 | Adaptive ritual timing | V2 |
| INT-13 | Geofence-triggered nudge | V2 |
| INT-14 | Bubble capture during declared work hours | V2 |
| INT-15 | Commute brief via Android Auto / Bluetooth | V3 |
| INT-16 | Wear OS wrist confirmations | V3 |

### 12.9 Review (REV)

| ID | Feature | Phase |
|---|---|---|
| REV-01 | Evening close ritual | V0 |
| REV-02 | Free-form day recap parsing | V0 |
| REV-03 | Weekly audit | V1 |
| REV-04 | Rolling 30-day trend charts | V1 |
| REV-05 | Domain scorecard | V1 |
| REV-06 | Time attribution — where the week went | V2 |
| REV-07 | Monthly retro against goals | V2 |
| REV-08 | Decision audit at 90 days | V2 |
| REV-09 | Prediction accuracy reporting | V2 |
| REV-10 | Quarterly direction review | V2 |
| REV-11 | Annual synthesis | V3 |

### 12.10 Life domains (DOM)

| ID | Domain | Contents | Phase |
|---|---|---|---|
| DOM-01 | Health — movement | Gym schedule, workout log, step targets, session alarms | V0 |
| DOM-02 | Health — sleep | Bedtime rule, wind-down alarm, duration tracking | V0 |
| DOM-03 | Health — food | Meal photo log, water reminders, eating-window rule | V1 |
| DOM-04 | Health — clinical | Blood panels stored, retest reminders, medication schedule, appointments | V1 |
| DOM-05 | Health — body | Weight trend, measurements, monthly progress photos | V1 |
| DOM-06 | Health — recovery | Illness mode, rest days, injury tracking | V2 |
| DOM-07 | Work — deliverables | Commitments from meetings and chats, deadlines, status | V0 |
| DOM-08 | Work — meetings | Prep notes, action item extraction, debriefs | V1 |
| DOM-09 | Work — waiting-on | What colleagues owe you, chase cadence | V1 |
| DOM-10 | Work — career | Skills, learning goals, review-cycle prep, achievements log | V2 |
| DOM-11 | Money — bills | Recurring bills, due alarms, payment confirmation | V1 |
| DOM-12 | Money — spending | Category tracking from payment notifications | V2 |
| DOM-13 | Money — obligations | EMIs, insurance renewals, SIPs, tax dates | V2 |
| DOM-14 | Money — position | Net worth snapshot, monthly update | V3 |
| DOM-15 | People — relationships | Contact cadence, last contact, open loops both directions | V1 |
| DOM-16 | People — occasions | Birthdays, anniversaries, promised follow-ups | V1 |
| DOM-17 | Home — errands | Repairs, purchases, recurring maintenance | V1 |
| DOM-18 | Home — documents | Passport, licence, insurance expiry | V2 |
| DOM-19 | Learning | Books, courses, deliberate practice, progress | V2 |
| DOM-20 | Travel | Trips, bookings, packing lists, pre-departure checks | V2 |
| DOM-21 | Admin | Anything with a deadline and a government office attached | V2 |
| DOM-22 | Personal projects | Side work, creative projects, their own goal tree | V2 |

### 12.11 Resilience & anti-burden (RES)

| ID | Feature | Phase |
|---|---|---|
| RES-01 | Auto-archive after 45 days dormant | V1 |
| RES-02 | Domain bankruptcy — clear a backlog in one action | V1 |
| RES-03 | Re-entry flow after 3+ missed days — no shame, no dump | V1 |
| RES-04 | Monthly reduction proposal | V2 |
| RES-05 | No badges, no counts, no red | V0 |
| RES-06 | Neutral language enforcement in all generated output | V0 |
| RES-07 | Load score — refuses to plan a full day when load is high | V1 |
| RES-08 | Ritual skip without penalty | V0 |
| RES-09 | Progressive feature disclosure | V1 |
| RES-10 | Annual data pruning proposal | V3 |

### 12.12 Interface (UX)

| ID | Feature | Phase |
|---|---|---|
| UX-01 | Now screen — 6 objects maximum | V0 |
| UX-02 | Hold-to-talk primary action | V0 |
| UX-03 | Talk screen | V0 |
| UX-04 | Close screen card stack | V0 |
| UX-05 | Look screen with search and 4 collapsed sections | V1 |
| UX-06 | Universal one-gesture correction | V0 |
| UX-07 | Navigation depth ≤ 2, enforced | V0 |
| UX-08 | Dark mode / OLED black | V1 |
| UX-09 | Single-hand reachability for all primary actions | V0 |
| UX-10 | Offline visual state — clear when running in fallback | V1 |

### 12.13 System (SYS)

| ID | Feature | Phase |
|---|---|---|
| SYS-01 | Declarative Task definitions | V0 |
| SYS-02 | Context assembler with recipes and budgets | V0 |
| SYS-03 | Prompt registry on disk, versioned | V0 |
| SYS-04 | Capability-level provider adapters | V0 |
| SYS-05 | Two-tier model routing | V0 |
| SYS-06 | Structured output validator with repair | V0 |
| SYS-07 | Deterministic fallback pipeline | V0 |
| SYS-08 | Foreground service + WorkManager | V0 |
| SYS-09 | Encrypted local DB | V0 |
| SYS-10 | Nightly encrypted backup to Drive | V0 |
| SYS-11 | Full JSON export | V0 |
| SYS-12 | Silent-failure detection and alerting | V0 |
| SYS-13 | Offline capture queue | V1 |
| SYS-14 | Evaluation harness, 25 golden inputs | V1 |
| SYS-15 | Prompt-prefix caching | V1 |
| SYS-16 | PII redaction before egress | V1 |
| SYS-17 | Token and cost dashboard | V1 |
| SYS-18 | Prompt version comparison tooling | V2 |
| SYS-19 | Per-domain egress control | V2 |
| SYS-20 | Single tool-calling task for open Q&A | V2 |
| SYS-21 | Optional VPS scheduler | V2 |
| SYS-22 | Multi-device sync | V3 |

**Totals:** 8 + 18 + 16 + 14 + 16 + 13 + 11 + 16 + 11 + 22 + 10 + 10 + 22 = **187 features · 4 screens**

---

# PART V — EXECUTION

## 13. Build phases

| Phase | Dates | Deliverable |
|---|---|---|
| Manual validation | 26 Jul – 2 Aug | No code. Rituals by hand. Output: list of moments the assistant was wanted, and for what. This list overrides §12. |
| **V0** | 3 – 14 Aug | 12 days. Capture, memory, plan, brief, alarms, close, accountability, conversation, orchestration |
| Daily use begins | 15 Aug | Used every day regardless of state |
| **V1** | 18 Aug – 14 Sep | Passive capture, anticipation, health domain, weekly audit, resilience |
| **Checkpoint** | 20 Sep | §1.2 evaluated. Continue, redesign, or stop. |
| **V2** | 21 Sep – 2 Nov | Remaining domains, horizons, decision support, drafting |
| **V3** | Conditional | Only what the usage log proves is wanted |

### 13.1 V0 day-by-day

| Day | Work | Feature IDs |
|---|---|---|
| 1 | Project skeleton, Room schema, SQLCipher | SYS-08, SYS-09 |
| 2 | LLM abstraction, router, validator, prompt registry | SYS-01 to SYS-07 |
| 3–4 | Hold-to-talk, transcription, capture surfaces | CAP-01 to CAP-04 |
| 5 | Fact extraction, review queue, correction | MEM-01 to MEM-06 |
| 6 | Commitment state machine, prediction ledger | ACC-01, ACC-05 |
| 7 | Calendar sync, rule engine, life intake | CAP-05, DIR-03, FDN-01/02/04/05 |
| 8 | Plan generation, what-now, minimum viable day | DIR-01, DIR-02, DIR-04 |
| 9 | Morning brief TTS, alarms, notification budget | INT-01 to INT-08 |
| 10 | Evening close card stack, day recap | ACC-02 to ACC-04, REV-01, REV-02 |
| 11 | Conversation surface, recall, fact emission | CNV-01 to CNV-04 |
| 12 | Now screen, backup, export, failure alerting | UX-01 to UX-09, SYS-10 to SYS-12 |

Day 2 is unglamorous plumbing with nothing to show — the phase where side projects die. Day 3 must produce a working voice capture regardless of how rough day 2 was.

---

## 14. Android integration surface

### 14.1 Permissions

| Permission / API | For | Risk |
|---|---|---|
| `RECORD_AUDIO` | Voice | Low |
| `SCHEDULE_EXACT_ALARM` | Precise reminders | Restricted since Android 13, needs explicit grant |
| `POST_NOTIFICATIONS` | All prompts | Low |
| `FOREGROUND_SERVICE` | Persistent listener | Requires visible ongoing notification |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Survive OEM killers | Highest technical risk, §16.1 |
| `NotificationListenerService` | Commitment scraping | Special settings grant; irrelevant when sideloading |
| `PACKAGE_USAGE_STATS` | Screen time | Special settings grant |
| Health Connect | Steps, sleep, weight, HR | Per-datatype grants |
| `ACCESS_BACKGROUND_LOCATION` | Geofences | Battery cost; V2 only |
| `CAMERA` | Meal and document photos | Low |
| Calendar API | Events | OAuth, §16.4 |
| Gmail API readonly | Commitment extraction | OAuth restricted scope, V2 |

### 14.2 Surfaces exploited

Quick Settings tile · lockscreen widget · home screen widget · notification actions · assistant gesture binding · share sheet · bubbles · Wear OS (V3) · Android Auto (V3) · DND integration.

---

## 15. Non-goals

| Not building | Why |
|---|---|
| Habit checkboxes and streaks | Break one, quit entirely. ACC-10 replaces it. |
| Auto-reply to email or messages | One bad send destroys trust in the whole system |
| Custom calendar UI | Google Calendar is source of truth |
| Web dashboard | Splitting platforms in month one is how V0 becomes V-never |
| On-device LLM | Not worth the complexity for one user |
| Long journaling or mood forms | Filled for nine days |
| Social, sharing, leaderboards | One user |
| Settings screen with 40 toggles | Config is procrastination with a UI |
| Play Store release | See P8 |
| Gamification of any kind | Motivation must not depend on the app |

---

## 16. Risks

**16.1 OEM background killing — HIGH.** Xiaomi, Oppo, Vivo, OnePlus, Realme, Samsung terminate background services aggressively. The 06:00 brief will silently fail with no error. *Mitigation:* foreground service + WorkManager + `setExactAndAllowWhileIdle` + manual battery exemption. Budget a full day. Verify across three untouched nights. SYS-12 detects silent misses.

**16.2 The input problem — HIGH.** Every app in this category dies when the user stops entering data around week three. *Mitigation:* P1, plus passive sources prioritised in V1. The ≤5 min/day criterion is the tripwire.

**16.3 Notification fatigue — HIGH.** One over-eager week and it is muted forever. Unrecoverable. *Mitigation:* INT-04 hard cap in code, INT-05 channels, INT-07 DND respect, ANT-02 one anticipation per day.

**16.4 OAuth token expiry — MEDIUM.** Google consent screens in testing mode issue refresh tokens expiring in 7 days. Calendar sync will break weekly and silently. *Mitigation:* decide before day 7 — publish the consent screen unverified, or accept weekly re-auth.

**16.5 Scope — MEDIUM.** 187 features is a six-month build if attempted in order. *Mitigation:* V0 is 12 days and ~50 features. The manual week's findings override §12.

**16.6 Conversation cost drift — MEDIUM.** Conversation is the only unbounded-cost surface. *Mitigation:* per-session token ceiling, SYS-17 daily dashboard.

**16.7 Transcription quality — MEDIUM.** On-device recognition degrades on long accented unstructured speech. *Mitigation:* hybrid routing; test with real samples before finalising CAP-01.

**16.8 Data loss — MEDIUM.** Single device holding externalised memory. *Mitigation:* SYS-10 from V0.

**16.9 Employment IP clause — UNRESOLVED.** Indian IT contracts routinely assign ownership of anything built during employment. Applies even to personal-use builds. *Mitigation:* read the contract before day 1.

**16.10 Over-trust — LOW but real.** As the assistant becomes reliable, judgment gets outsourced to it. *Mitigation:* REV-09 accuracy reporting keeps its fallibility visible; CNV-08 challenge mode keeps disagreement normal.

---

## 17. Open decisions

| # | Decision | By |
|---|---|---|
| 1 | Wearable purchase — DOM-02 and DOM-05 need it to avoid manual entry | 10 Aug |
| 2 | OAuth approach (§16.4) | Day 7 |
| 3 | Blood panel baseline before health domain design | 2 Aug |
| 4 | Actual ritual times — determined by manual week | 2 Aug |
| 5 | Which 3 domains activate first — determined by manual week | 2 Aug |
| 6 | Primary and secondary LLM provider | Day 2 |

---

## 18. Revision log

| Version | Date | Change |
|---|---|---|
| 0.1 | 25 Jul 2026 | Initial |
| 0.2 | 25 Jul 2026 | LLM orchestration layer |
| **1.0** | 25 Jul 2026 | Added conversation layer, bootstrap intake, goal/value hierarchy, anticipation engine, waiting-on tracking, resilience and anti-burden subsystem, minimalism resolution and 4-screen IA, correction paths, state and mode entities, Project and Reference fact types. 187 features. |
| 1.1 | — | To be revised 2 Aug from manual validation week |
