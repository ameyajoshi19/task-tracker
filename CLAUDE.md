# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Smart Task Scheduler — an Android app (Kotlin + Jetpack Compose) that intelligently schedules tasks on Google Calendar using Eisenhower matrix prioritization and a slot-centric best-fit algorithm.

## Architecture

MVVM with Clean Architecture, three layers:
- **Domain** (`domain/`) — models, repository interfaces, scheduling engine, validation. Pure Kotlin, no Android dependencies.
- **Data** (`data/`) — Room entities/DAOs, repository implementations, Google Calendar API client.
- **UI** (`ui/`) — Compose screens, ViewModels with StateFlow, navigation.

Data flows: UI → ViewModel → Use Case → Repository → Data Source. Calendar data also flows inward (free/busy reads).

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests (JVM)
./gradlew connectedDebugAndroidTest  # Run instrumented tests (requires emulator/device)
```

Run a single test class:
```bash
./gradlew test --tests "com.tasktracker.domain.scheduler.TaskSchedulerTest"
```

## Tech Stack

Kotlin 2.1, Jetpack Compose (Material 3), Room, Hilt, WorkManager, Google Calendar API, Compose Navigation.

## Key Design Decisions

- **Scheduling algorithm** is slot-centric best-fit: iterates available time slots chronologically, fills each with the highest-priority task that fits. This maximizes slot utilization (a 1-hr slot gets a 1-hr task even if a higher-priority 2-hr task exists).
- **All timestamps** use `Instant` (UTC) internally. `UserAvailability` uses `LocalTime` since it represents recurring daily windows.
- **Splittable tasks** can be broken across slots with a 30-minute minimum block size.
- **NeedsReschedule** (conflict resolution with task displacement) is implemented alongside Google Calendar sync, not in the standalone scheduling engine.

## Specs & Plans

- Design spec: `docs/superpowers/specs/2026-03-16-smart-task-scheduler-design.md`
- Plan 1 (Data + Scheduling): `docs/superpowers/plans/2026-03-16-plan1-data-layer-and-scheduling.md`
