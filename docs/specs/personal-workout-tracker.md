# Personal Workout Tracker (Arkham Batcomputer Edition)

**Triage Label**: `ready-for-agent` | **Type Label**: `feature` | **Target State**: `Todo`

## Problem Statement

Tracking workouts in a physical notebook breaks down once a training history grows: it becomes tedious to flip through pages to compare past performances, spot plateaus, or see progression across different training styles. As a solo athlete who trains Calisthenics (Push, Pull, Leg) alongside weekly Conditioning — and plans to add Cardio and Weight Lifting over time — there is no quick way to visualize consistency, Exercise progression, and Personal Records in graphs and numbers, nor a clean personal Android tool tailored to this workflow without bloat like meal trackers, accounts, or bright generic fitness themes.

## Solution

Build a personal, 100% offline-first native Android workout tracker in Jetpack Compose with a *Batman: Arkham Knight* Batcomputer hacker-green aesthetic. The app starts completely blank so the user can build their own Exercise library and Routines from scratch, log Sessions either live (with auto-saved drafts and a haptic Rest Timer) or post-workout/backdated for any date, and inspect long-term progress through custom HUD charts, Personal Records, consistency metrics, and Bodyweight trends. All data is stored locally in a sync-ready database with JSON export/import today, prepared for future sync to a custom backend via an API key.

## User Stories

1. As an athlete, I want the app to open directly onto the Home screen with a blank slate and no forced onboarding wizard, so that I can immediately explore and test the input workflow myself.
2. As an athlete, I want to see a personalized tactical greeting on the Home screen with an editable display name, so that the app feels like my personal Batcomputer.
3. As an athlete, I want a prominent `START` hero card on the Home screen that opens a picker to launch an existing Routine or start a blank Session, so that I can begin training in two taps.
4. As an athlete, I want the `START` picker to highlight my suggested next Routine based on my recent history, so that I can follow my Push/Pull/Leg/Conditioning rotation effortlessly.
5. As an athlete, I want an interactive Monday–Sunday weekly calendar strip on the Home screen that marks days with completed Sessions using a glowing hacker-green indicator, so that I can see my weekly consistency at a glance.
6. As an athlete, I want to tap any date on the weekly calendar strip to inspect the Sessions logged on that day, so that I can review what I trained without digging through menus.
7. As an athlete, I want horizontal Discipline filter pills (`Calisthenics`, `Conditioning`, `Cardio`, `Weight Lifting`) on the Home screen, so that I can quickly filter my Routines by training focus.
8. As an athlete, I want a horizontal carousel of tactical HUD Routine cards showing the primary Discipline, Routine name, Exercise count, and last/estimated duration, so that I can launch any Routine directly from Home.
9. As an athlete, I want a Recent Activity list on the Home screen displaying my latest completed Sessions with title, timestamp, and duration, so that I can quickly review or tap into recent training logs.
10. As an athlete, I want a 4-tab bottom navigation bar (`Home`, `Workout`, `Progress`, `Profile`) with a center floating `+` button, so that navigation matches my preferred layout without an unused Meals tab.
11. As an athlete, I want the center `+` button to let me either start a live blank Session or log a completed/backdated Session for any past date, so that I can migrate past notebook entries or log workouts after the fact.
12. As an athlete, I want to create, edit, and delete Exercises in my library on the Workout tab, assigning each Exercise a primary Discipline and a Tracking Mode, so that I control my own movement catalog.
13. As an athlete, I want to model each calisthenics progression (e.g., Push-up, Archer Push-up, One-Arm Push-up) as its own Exercise, so that charts and Personal Records remain clean and comparable.
14. As an athlete, I want to create a new Exercise inline directly from the Routine builder or active Session picker without leaving my current screen, so that starting from a blank library never interrupts my flow.
15. As an athlete, I want to log Exercises using the `Reps & Load` Tracking Mode (with optional added load, defaulting to bodyweight when load is zero), so that I can track both bodyweight calisthenics, weighted calisthenics, and future weight lifting.
16. As an athlete, I want to log Exercises using the `Hold / Duration` Tracking Mode (recording hold seconds per Set), so that I can track isometric calisthenics skills like L-sits, planks, and dead hangs.
17. As an athlete, I want to log Exercises using the `Distance & Time` Tracking Mode, so that I can track cardio activities like running, cycling, or rowing.
18. As an athlete, I want to log Exercises using the `Conditioning / Rounds` Tracking Mode (recording rounds/reps completed and total duration), so that I can track AMRAP, EMOM, and circuit conditioning workouts.
19. As an athlete, I want to add optional free-text notes to both a Session and individual Exercises within a Session, so that I can record form cues, RPE, or how the workout felt just like in my notebook.
20. As an athlete, I want to create, reorder, edit, and delete reusable Routines on the Workout tab, assigning one primary Discipline per Routine while allowing Exercises from any Discipline inside it, so that I can mix a Conditioning finisher into a Calisthenics Push Routine.
21. As an athlete, I want to log individual Sets for each Exercise in a Session (e.g., Set 1: 12 reps, Set 2: 10 reps, Set 3: 8 reps), so that real rep drop-off across sets is accurately recorded.
22. As an athlete, I want to mark specific Sets as Warm-up Sets as distinct from Working Sets, so that warm-up efforts are kept in Session history without skewing my Personal Records or progression charts.
23. As an athlete, I want every Session to be stored as an independent snapshot, so that editing a Routine later never mutates my past Session history.
24. As an athlete, I want the option to add, remove, swap, or adjust Exercises and Sets mid-Session and be prompted *"Update Routine with these changes?"* upon finishing, so that my Routines evolve naturally without accidental overwrites.
25. As an athlete, I want a live Session to display a running workout timer and auto-save every Set change as an `IN_PROGRESS` draft, so that locking my phone or switching apps never loses my workout.
26. As an athlete, I want a `"RESUME ACTIVE SESSION"` HUD banner on the Home screen whenever an `IN_PROGRESS` Session exists, so that I can jump right back in or discard it cleanly.
27. As an athlete, I want a compact Batcomputer Rest Timer to auto-start when I check off a Set during a live Session, with `-15s`, `+15s`, and `Skip` controls plus a subtle haptic buzz at `00:00`, so that my rest periods stay disciplined without leaving the logging screen.
28. As an athlete, I want to view, edit, or delete any completed Session from Recent Activity or the calendar strip, so that I can fix typos or add missed notes later.
29. As an athlete, I want a Progress tab with a time-range switcher (`1W`, `1M`, `3M`, `1Y`, `ALL`), so that I can zoom in on recent weeks or inspect long-term progression.
30. As an athlete, I want a Consistency view on the Progress tab showing total Sessions, active weekly streak, and a breakdown by Discipline, so that I can verify I'm hitting my Push/Pull/Leg + Conditioning balance.
31. As an athlete, I want an Exercise Progression view on the Progress tab where I can pick any Exercise and see a hacker-green HUD line chart over time plus its Personal Records, so that I can visually confirm I'm getting stronger.
32. As an athlete, I want `Reps & Load` Exercise charts to plot Total Reps and Best Set Reps for bodyweight Exercises, and Total Volume (`reps × load`) and Max Load for weighted Exercises (with a quick metric toggle), so that progression is measured accurately for both calisthenics and weight lifting.
33. As an athlete, I want `Hold / Duration` Exercise charts to plot Longest Hold and Total Hold Time, `Distance & Time` charts to plot Distance and Pace, and `Conditioning / Rounds` charts to plot Rounds Completed and Total Duration, so that every Tracking Mode has meaningful visuals.
34. As an athlete, I want a Summary Stats view on the Progress tab showing total training time, total completed Sessions, and a standalone Bodyweight Entry trend chart, so that I can track overall effort and body trends alongside strength metrics.
35. As an athlete, I want to log dated Bodyweight Entries from the Profile (or Progress) screen completely separately from Exercise load, so that my bodyweight trend is tracked without distorting bodyweight Exercise load calculations.
36. As an athlete, I want to configure my display name, measurement units (`kg`/`lb`, `km`/`mi`), and default Rest Timer duration on the Profile tab, so that the app matches my personal preferences.
37. As an athlete, I want to export my entire training database to a local JSON file and import/restore from a JSON backup on the Profile tab, so that I never lose my training history when switching devices.
38. As an athlete, I want a placeholder "Server & API Key Sync" section on the Profile tab clearly marked as offline-only for now, so that the UI is ready for my upcoming backend integration.
39. As an athlete, I want the entire app styled with a dark *Batman: Arkham Knight* Batcomputer hacker-green theme (matte obsidian surfaces, phosphor green telemetry, chamfered `1dp` HUD panels, monospace telemetry numerals, subtle scanline/grid texture, and snappy 150–250ms HUD animations), so that logging workouts feels crisp, high-contrast, and motivating.

## Implementation Decisions

- **Single-Activity Jetpack Compose Architecture**: Build the app with Jetpack Compose + Material 3, Jetpack Navigation Compose (4 bottom tabs: `Home`, `Workout`, `Progress`, `Profile` + center `+` FAB sheet + full-screen Active/Backdated Session and Session Detail routes), and Kotlin Coroutines/Flow.
- **Lightweight Manual Dependency Injection**: Use a single `AppContainer` at application startup to wire the database, repositories, analytics calculators, backup serializer, and `Clock` — avoiding Hilt boilerplate for a personal app.
- **Offline-First, Sync-Ready Local Storage (ADR-0001)**: Persist all domain entities in Room (via KSP) using client-generated UUID primary keys, `createdAt` and `updatedAt` timestamps, `deletedAt` soft-delete tombstones, and a `syncStatus` (`PENDING` / `SYNCED`) column across every entity, accessed strictly through a repository boundary. This enables future API-key backend sync without schema rewrites.
- **Domain Entities & Schema**:
  - `Exercise`: UUID, name, primary `Discipline`, `Tracking Mode`, optional default notes, sync metadata.
  - `Routine` & `RoutineExercise`: UUID, name, primary `Discipline`, ordered Exercise references with optional default target set counts, sync metadata.
  - `Session`, `SessionExercise`, & `SessionSet`: UUID, optional originating `Routine` UUID, title, primary `Discipline`, status (`IN_PROGRESS` / `COMPLETED`), `startedAt`, `completedAt`, `durationSeconds`, notes, ordered snapshot of Exercises and individual `Set` rows (each storing mode-specific numeric fields, completion state, and `isWarmup` flag), sync metadata.
  - `BodyweightEntry`: UUID, `recordedAt`, weight (stored canonically in metric `kg` and converted at the UI boundary based on unit preference), sync metadata.
  - `UserPreferences`: Display name (default `"OPERATOR"`), weight unit (`kg`/`lb`), distance unit (`km`/`mi`), default Rest Timer duration (default `90s`), stored locally.
- **Canonical Units**: Store weights in `kg` and distances in `km` internally in Room and JSON backups; convert to `lb`/`mi` purely at the presentation/input boundary so switching units never corrupts historical data.
- **Snapshot & Routine-Update Flow**: Starting a Session from a Routine copies its Exercises into `SessionExercise` rows immediately. Finishing a Session compares its Exercise list against the originating Routine; if modified, prompts *"Update Routine with these changes?"* and only updates the Routine if confirmed.
- **Analytics & PR Engine**: Pure Kotlin domain calculators compute Personal Records, streaks, Discipline breakdowns, and per-Tracking-Mode chart series from completed `Session`s, strictly excluding `Warm-up Set`s and `IN_PROGRESS` or soft-deleted records.
- **Custom Compose Canvas HUD Charts**: Render line charts, Discipline breakdown bars, and segmented tactical rings directly with Jetpack Compose `Canvas` (supporting touch inspection crosshairs, monospace axis labels, and hacker-green phosphor styling) rather than pulling in a third-party charting library.
- **JSON Backup & Restore**: Use Kotlin serialization to export/import a versioned JSON archive of all non-deleted entities (preserving UUIDs and timestamps) via Android's Storage Access Framework (`ActivityResultContracts.CreateDocument` / `OpenDocument`).
- **Arkham Knight Hacker-Green Design System**: Define a dedicated dark-only Material 3 color scheme (`#060A08` / `#0D1411` matte obsidian surfaces, `#00FF66` / `#00E676` phosphor green primary telemetry, `#1A4D33` muted terminal emerald grids/dividers, `#E6FFF0` high-contrast mint-white text, tactical amber/red reserved for PR badges and destructive actions), custom chamfered `1dp` HUD card shapes, monospace numeral typography, and fast (150–250ms) staggered HUD reveal, scanline, and phosphor pulse animations.

## Testing Decisions

- **What Makes a Good Test**: Tests verify observable external behavior and domain invariants (what the user sees, taps, saves, and queries), never private implementation details or internal mock call counts.
- **Testing Seams**: Keep injection minimal with two top-level seams at `AppContainer`:
  1. **Storage seam**: Swap the on-device Room database for an in-memory Room database in tests.
  2. **Time seam (`Clock`)**: Inject a controllable fake `Clock` in tests so Monday–Sunday calendar windows, streaks, `1W / 1M / 3M / 1Y / ALL` filters, Rest Timer ticks, and sync timestamps are 100% deterministic.
- **Modules Under Test**:
  - **End-to-end UI + Persistence (`androidTest`)**: Compose UI tests wired to `AppContainer` with in-memory Room + fake `Clock`, exercising full journeys: blank-slate empty states, inline Exercise creation, Routine creation, live Session logging (Working vs. Warm-up Sets), `IN_PROGRESS` resume recovery, mid-Session edits + *"Update Routine?"* prompt, backdated Session logging, Progress charts/PRs, and Profile unit switching.
  - **Domain Analytics, Repository & Backup (`test`)**: Fast JVM unit tests covering PR calculation across all 4 Tracking Modes, Warm-up Set exclusion, streak & Discipline breakdown math, time-range filtering, canonical unit conversion, sync metadata transitions (`PENDING` on create/update/soft-delete), and JSON export/import round-trips.
- **Prior Art**: This is a greenfield repo containing only the default Android template `ExampleUnitTest` and `ExampleInstrumentedTest`, so these suites will establish the project's test conventions.

## Out of Scope

- Cloud backend synchronization, API-key authentication networking, and conflict resolution (deferred until the user's backend is ready; only the local sync-ready schema and Profile placeholder are in scope now).
- Multi-user accounts, authentication, social feeds, or sharing workouts.
- Meal tracking, nutrition/calorie logging, or macro calculators.
- Pre-seeded Exercise libraries, starter Routines, or instructional workout videos/thumbnails.
- Wearable/Health Connect/Google Fit integration or GPS route tracking for outdoor cardio.
- Light theme (app is strictly dark Arkham Batcomputer hacker-green).

## Further Notes

- Domain vocabulary is governed by `GLOSSARY.md` at the repo root (`Discipline`, `Exercise`, `Tracking Mode`, `Routine`, `Session`, `Set`, `Working Set`, `Warm-up Set`, `Personal Record (PR)`, `Bodyweight Entry`).
- Offline-first sync-ready storage architecture is governed by `docs/adr/0001-offline-first-sync-ready-storage.md`.
- Published to Linear as [E4E-39](https://linear.app/e4e-han/issue/E4E-39/personal-workout-tracker-arkham-batcomputer-edition) (`Todo`, labels: `ready-for-agent`, `Feature`).
