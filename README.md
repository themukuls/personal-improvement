# Chief of Staff

A private, sideloaded, single-user Android "chief-of-staff" — the V0 vertical slice of the
[Personal AI Assistant build specification](docs/SPEC.md). It is not a to-do app. It aims to do four
things a tracker cannot: **Know** (hold a durable model of your life), **Plan** (decide what should
happen today and what should not), **Verify** (close the loop on every commitment), and **Discuss**
(think through problems with full context, and push back).

> Status: V0 vertical slice. Builds with Android Studio / Gradle; the app compiles to a coherent,
> navigable product with the four screens, the encrypted life model, the LLM orchestration layer,
> the ritual/alarm machinery, and first-run seed data that reproduces the design.

## The design it implements

The UI follows the **"Personal chief-of-staff — visual directions"** design, which explored three
takes on the Now screen and recommended one. This app builds that recommendation:

- **Direction 1a (Quiet Ledger)** as the base — soft pastel neumorphism, no streaks, no counts, no
  colour-coded pressure; a single anticipation card and a "NOT TODAY" refusals line.
- **the "RIGHT NOW" line from 1c** at the top, so the app answers *"what do I do?"* before you read a
  list.
- **1b (Standing Ground) is deliberately rejected.** Its streak, reset-threat, badge and unlock card
  are exactly what §15 of the spec says kill the product on the day you break one. None of it ships.

See [`docs/DESIGN.md`](docs/DESIGN.md) for the full read of the design and how each element maps to
Compose, and [`docs/design-reference.png`](docs/design-reference.png) for the rendered source.

## The four screens (§3.2)

| Screen | Role |
|---|---|
| **Now** | Home. Date + one status line, the RIGHT NOW answer, the LOOKING AHEAD card, 3–6 TODAY items, the NOT TODAY refusals. ≤ 6 objects, ever. |
| **Talk** | The conversation surface. Full memory, empty by default, voice or text. Where planning and thinking happen. |
| **Close** | The evening card stack. One card at a time; swipe right/left/up/down = done/skipped/defer/drop. |
| **Look** | Everything else. Search over the FTS index + four collapsed sections (Timeline, Trends, Domains, People). |

## Architecture

The §4 layers, assembled in a hand-wired [`AppContainer`](app/src/main/java/com/chiefofstaff/AppContainer.kt):

```
CAPTURE      voice / text / tile / share / calendar   →  immutable, FTS-indexed captures
MEMORY       Room + SQLCipher life graph (§7)
   ├── DIRECTION       plan · what-now · rule referee · minimum-viable-day
   ├── ANTICIPATION    nightly deterministic scans → one item/day
   └── CONVERSATION    full-context thinking partner
INTERVENTION  morning brief (TTS) · exact alarms · hard notification budget
REVIEW        evening close · prediction ledger · consistency
```

Full detail in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### Load-bearing invariants (the spec's "laws")

- **Captures are immutable and always indexed first** (P5) — nothing you say is ever lost, even offline.
- **Every commitment gets a verdict** (P4) — the state machine's only exits are done/skipped/dropped/archived.
- **The 06:00 brief always fires** (P11) — a deterministic fallback runs with no network or model.
- **The notification budget is enforced in code** (INT-04) — 3/day discretionary, rituals bypass; getting muted is unrecoverable.
- **Only assembled, redacted context leaves the device** (§6.8) — never the raw log, never the DB.

## Building

```bash
./gradlew assembleDebug        # requires the Android SDK (Android Studio or a configured SDK)
```

Minimum SDK 29, target 34, Kotlin + Jetpack Compose. The project is **sideload-only** (P8): it uses
restricted APIs (exact alarms, notification listener, foreground service) that a Play Store release
would reject, and that is by design.

### LLM providers (optional)

With **no API key set the app still runs** — on the deterministic paths plus an offline stub, so the
rituals work. To enable reasoning, set a key (held in Keystore-guarded encrypted storage, never in the
APK) for Claude and/or an OpenAI-compatible endpoint (GPT / Grok). The Router picks among them by
tier, availability and cost, with a secondary on failure.

Model IDs used by the Claude adapter default to the current Claude family (Opus for the flagship
tier, Haiku for the cheap extraction tier); swap them in `ClaudeAdapter` if you pin different ones.

## What is intentionally *not* here (V0 scope)

Per §13, V0 is 12 days and ~50 features. Passive capture (notification/health/screen-time), the
weekly audit, most life domains, horizons, drafting, and OLED-dark mode (UX-08) are V1/V2 and are
scaffolded but not built. Non-goals from §15 (streaks, gamification, auto-send, a settings screen of
40 toggles) are not built on purpose.
