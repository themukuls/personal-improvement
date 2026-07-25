# Design read — how the "visual directions" map to this app

The source design (`design-reference.png`, rendered from `Assistant Directions.dc.html`) is a
judgement artifact titled **"Personal chief-of-staff — visual directions"**. It presents three takes
on the **Now screen** in a soft pastel neumorphic language and recommends one. This app builds the
recommendation.

## The three directions

| | Name | Intent | Verdict |
|---|---|---|---|
| **1a** | **Quiet Ledger** | Spec-pure. No streaks, no counts, no colour-coded pressure. One anticipation card ("LOOKING AHEAD"), a "NOT TODAY" refusals line, rolling consistency stated as a fact. | ✅ **Base** |
| **1b** | **Standing Ground** | The psychology notes applied literally: a 14-day streak with loss-aversion ("miss tonight and the run resets to zero"), an identity line, a Zeigarnik % bar, a variable-reward "UNLOCKED" card, a "2 of 4 done" count, a red badge on Close. | ❌ **Rejected** — every element violates a stated rule (§15). "Here to be judged, not shipped." |
| **1c** | **One Thing** | The most reduced reading: one decided next action as a hero card, a WINDOW time-block, the rest of the day a peek you drag up, voice the only visible control. | 🔶 **Borrow one idea** |

**The design's own recommendation, which this app follows:** *1a as the base, with the "RIGHT NOW"
line borrowed from 1c at the top, so the app answers "what do I do?" before you read a list.* 1b's
only defensible element (a progress bar) is left out because the streak/badge/reset-threat it travels
with is exactly what kills the product on the day you break one.

## The visual system → Compose

| Design element | Implementation |
|---|---|
| Soft neumorphic surface on one near-white base | [`Neu.kt`](../app/src/main/java/com/chiefofstaff/ui/theme/Neu.kt) `neuSurface` — dual soft shadow (light up-left, dark down-right) via a framework `setShadowLayer` |
| Base `#EEF1F6`, card `#F0F3FA`, ink `#2E3140` | [`Color.kt`](../app/src/main/java/com/chiefofstaff/ui/theme/Palette) `Palette`, sampled from the PDF |
| Content sans + **monospace** for times & ALL-CAPS labels | [`Type.kt`](../app/src/main/java/com/chiefofstaff/ui/theme/Type.kt) — `ContentFamily` + `MonoFamily`, plus `Mono.SectionLabel/Time/Status` |
| Decorative pastel gradient (never a status signal) | `DecorativeGradient` / `AvatarGradient`; used only on the anticipation card, avatars, hold-to-talk |
| Date + `normal mode · 3 items · next 10:00 standup` | `NowScreen` header + `NowViewModel.statusLine` |
| **RIGHT NOW** hero line (from 1c) | `NowScreen` → `NowViewModel.rightNow` = `PlanGenerator.whatNow()` |
| **LOOKING AHEAD** card with Add commitment / Not useful | `GradientCard` + `AnticipationCard`; one item/day (ANT-02) |
| TODAY rows: mono time · title · neutral subline · hollow check | [`CommitmentRow`](../app/src/main/java/com/chiefofstaff/ui/components/CommitmentRow.kt) + `NeuCheckbox` |
| **NOT TODAY** refusals | `RuleEngine.check()` → excluded from TODAY, shown as refusal lines |
| Bottom nav pill `Now · Talk · Close · Look` + separate gradient mic | [`BottomNav.kt`](../app/src/main/java/com/chiefofstaff/ui/components/BottomNav.kt) `BottomBar` |
| No badges, no counts, no red | Enforced throughout (RES-05); the rejected 1b is where those live |

The seed data ([`SeedData.kt`](../app/src/main/java/com/chiefofstaff/system/SeedData.kt)) reproduces
the exact example content from Direction 1a (Gym — legs, Q3 capacity plan to Rakesh, Lab — lipid panel
result, the passport-expiry anticipation, the deep-work rule the "Sprint retro" collides with) so a
fresh install opens on the screen the design shows.

## What about dark mode?

The design ships a single light neumorphic scheme, and OLED-black dark mode is a V1 item (UX-08). This
slice commits fully to the light look rather than half-building two themes.
