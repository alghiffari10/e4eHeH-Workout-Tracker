# e4eHeH Workout

A personal, 100% offline-first native Android workout tracker built with Jetpack Compose and styled after a *Batman: Arkham Knight* Batcomputer HUD.

Designed for solo multi-discipline training without accounts, meal trackers, or pre-seeded bloat: the app opens to a clean blank slate so you can define your own **Exercises** and **Routines**, log live or backdated **Sessions**, and inspect long-term progression through custom HUD telemetry charts and **Personal Records**.

---

## Features

### Home Command Center
- **Tactical Greeting**: Personalized header with an editable operator display name (default `OPERATOR`).
- **`START` Hero Picker**: Start a blank **Session** or launch a **Routine** in two taps, with a suggested next Routine based on recent training history.
- **Resume Active Session Banner**: Surfaces any auto-saved `IN_PROGRESS` **Session** directly on Home so you can jump back in or discard it cleanly.
- **Interactive Weekly Calendar Strip**: Monday–Sunday strip highlighting days with completed **Sessions**; tap any day to inspect what was logged.
- **Discipline Filter & Routine Carousel**: Filter **Routines** by **Discipline** (`Calisthenics`, `Conditioning`, `Cardio`, `Weight Lifting`) and launch them straight from Home.
- **Recent Activity**: Quick access to your latest completed **Sessions** with title, timestamp, and duration.

### Workout Library (Exercises & Routines)
- **Custom Exercise Library**: Create, edit, and delete **Exercises**, each assigned a primary **Discipline** and **Tracking Mode**.
- **Inline Exercise Creation**: Add new **Exercises** directly from the Routine builder or an active Session without leaving your flow.
- **4 Tracking Modes**:
  - **Reps & Load**: Reps with optional added load (defaults to bodyweight when load is `0`), supporting both calisthenics and weight lifting.
  - **Hold / Duration**: Isometric hold duration in seconds per **Set** (e.g., L-sits, planks, dead hangs).
  - **Distance & Time**: Distance and duration for cardio activities (e.g., running, cycling, rowing).
  - **Conditioning / Rounds**: Completed rounds and total duration for AMRAP, EMOM, and circuit work.
- **Reusable Routines**: Build ordered **Routines** under one primary **Discipline** while mixing **Exercises** from any Discipline, with optional default target **Set** counts.

### Session Logging (Live & Backdated)
- **Quick `+` Action**: Launch a live blank **Session** or log a backdated **Session** for any past date.
- **Independent Session Snapshots**: Starting from a **Routine** snapshots its **Exercises** into the **Session** so later Routine edits never mutate past history.
- **Working vs. Warm-up Sets**: Tag individual **Sets** as **Warm-up Sets** to keep them in history while excluding them from **Personal Records** and progression charts.
- **Auto-Saved Drafts & Live Timer**: Every **Set** edit persists immediately to an `IN_PROGRESS` draft alongside a running workout timer.
- **Haptic Rest Timer**: Auto-starts when checking off a **Set** during a live **Session**, with `-15s`, `+15s`, `Skip`, and a haptic buzz at `00:00`.
- **Routine Update Prompt**: If you modify **Exercises** mid-Session, finishing prompts *"Update Routine with these changes?"* instead of silently overwriting.
- **Session Detail Editor**: View, edit, or delete any completed **Session** (including Session-level and Exercise-level notes).

### Progress & Telemetry
- **Time-Range Switcher**: Slice all analytics across `1W`, `1M`, `3M`, `1Y`, and `ALL`.
- **Consistency & Discipline Breakdown**: Track total completed **Sessions**, active weekly streak, and training distribution across **Disciplines**.
- **Exercise Progression & Personal Records**: Custom Jetpack Compose `Canvas` HUD line charts with touch inspection crosshairs and mode-specific metrics:
  - *Reps & Load*: Total Reps / Best Set Reps (bodyweight) or Total Volume / Max Load (weighted).
  - *Hold / Duration*: Longest Hold & Total Hold Time.
  - *Distance & Time*: Distance & Pace.
  - *Conditioning / Rounds*: Rounds Completed & Total Duration.
- **Summary Stats & Bodyweight Trend**: Total training time, completed **Sessions**, and a standalone **Bodyweight Entry** trend chart tracked independently from **Exercise** load.

### Profile & Data Portability
- **Operator Preferences**: Configure display name, weight unit (`kg` / `lb`), distance unit (`km` / `mi`), and default Rest Timer duration.
- **Canonical Metric Storage**: Weights (`kg`) and distances (`km`) are stored canonically in Room and JSON backups, converting at the UI boundary so switching units never distorts history.
- **Bodyweight Entries**: Full CRUD for dated bodyweight measurements.
- **JSON Export & Import**: Export/restore all non-deleted database records as a versioned JSON archive via Android's Storage Access Framework, preserving UUIDs and timestamps.
- **Sync-Ready Placeholder**: Dedicated offline-only *"Server & API Key Sync"* section ready for future custom backend integration.

---

## Visual Aesthetic

Strictly dark-only *Batman: Arkham Knight* Batcomputer HUD theme:
- **Surfaces**: Matte obsidian (`#060A08`, `#0A110E`, `#0D1411`, `#121D18`)
- **Primary Telemetry**: Phosphor green (`#00FF66`, `#00E676`) and terminal emerald grids (`#1A4D33`)
- **Typography & Geometry**: Monospace telemetry numerals, high-contrast mint-white text (`#E6FFF0`), chamfered `1dp` HUD panels, tactical amber (`#FFB300`) for PRs, and alert red (`#FF3B5C`) for destructive actions.

---

## Tech Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin `2.2.10` |
| UI | Jetpack Compose (BOM `2026.02.01`), Material 3, Navigation Compose `2.10.2`, custom `Canvas` HUD charts |
| Persistence | Room `2.8.5` (KSP `2.3.6`), exported schemas (`v1`–`v5`) with explicit migrations |
| Async & Serialization | Kotlin Coroutines & Flow `1.11.0`, `kotlinx.serialization` JSON `1.8.1` |
| Dependency Injection | Manual `AppContainer` (no Hilt boilerplate) with swappable Room & `Clock` seams |
| Testing | JUnit 4, Robolectric `4.18.1`, Compose UI Test (`sharedTest` across JVM and `androidTest`), Room Testing |
| Android & Build | `minSdk 24` (Android 7.0), `compileSdk 37`, `targetSdk 37`, AGP `9.3.3`, Gradle `9.5.0`, Java 11 bytecode with core library desugaring |

---

## Architecture & Project Structure

```text
app/src/
├── main/java/com/e4eheh/e4ehehworkout/
│   ├── data/
│   │   ├── backup/         # Versioned JSON archive codec & atomic BackupRepository
│   │   ├── local/          # Room WorkoutDatabase (v5), entities, DAOs, migrations
│   │   └── repository/     # Offline-first repository implementations
│   ├── di/                 # AppContainer (production & in-memory test graphs)
│   ├── domain/
│   │   ├── analytics/      # Pure Kotlin PR, progression, streak, Home & bodyweight calculators
│   │   ├── backup/         # Backup contracts and summary models
│   │   ├── model/          # Core domain models (Exercise, Routine, Session, etc.)
│   │   ├── repository/     # Repository interfaces
│   │   ├── timer/          # RestTimer controller
│   │   └── unit/           # Canonical kg/km <-> lb/mi converter
│   └── ui/
│       ├── components/     # Shared Arkham HUD chrome, bottom nav, pickers, dialogs
│       ├── navigation/     # Top-level tabs and routes
│       ├── screens/        # Home, Workout, Session (Active & Editor), Progress, Profile
│       └── theme/          # ArkhamColors, chamfered shapes, typography, HUD motion
├── sharedTest/kotlin/      # End-to-end Compose UI tests shared by JVM (Robolectric) & androidTest
├── test/java/              # Fast JVM unit, repository, analytics, migration & backup tests
└── androidTest/java/       # Instrumented Android tests
```

### Offline-First, Sync-Ready Storage
Per [ADR-0001](docs/adr/0001-offline-first-sync-ready-storage.md), every domain table uses:
- Client-generated **UUID** primary keys
- `createdAt` and `updatedAt` timestamps
- Soft-delete `deletedAt` tombstones
- `syncStatus` (`PENDING` / `SYNCED`) flags

This allows future API-key backend synchronization without breaking primary-key or history migrations.

---

## Getting Started

### Prerequisites
- Android Studio with Android SDK Platform **37** installed
- Gradle wrapper (`./gradlew`) — configured with Gradle `9.5.0` and the Foojay toolchain resolver (daemon JVM `25`)

### Clone & Build

```bash
git clone git@github.com:alghiffari10/e4eHeH-Workout-Tracker.git
cd e4eHeH-Workout-Tracker

# Build the debug APK
./gradlew assembleDebug

# Install on a connected Android device or emulator
./gradlew installDebug
```

### Running Tests

```bash
# Run all JVM unit tests, Room migration tests, and Robolectric Compose UI tests
./gradlew testDebugUnitTest

# Run instrumented tests on a connected Android device or emulator
./gradlew connectedDebugAndroidTest
```

---

## Documentation

- [GLOSSARY.md](GLOSSARY.md) — Canonical domain vocabulary (`Discipline`, `Exercise`, `Tracking Mode`, `Routine`, `Session`, `Set`, `Working Set`, `Warm-up Set`, `Personal Record`, `Bodyweight Entry`).
- [docs/adr/0001-offline-first-sync-ready-storage.md](docs/adr/0001-offline-first-sync-ready-storage.md) — Offline-first, sync-ready Room storage architecture decision.
- [docs/specs/personal-workout-tracker.md](docs/specs/personal-workout-tracker.md) — Full product specification and user stories.

---

## Roadmap

- **Custom Backend Sync**: API-key authenticated sync client using the existing UUID, tombstone, and `syncStatus` metadata.
