# Login Page, Settings Restructure & Availability Editor Redesign

**Date:** 2026-03-19

## Overview

Three interconnected UI improvements: (1) a branded full-screen login page replacing the current onboarding sign-in step, (2) restructuring the Settings screen from a single scroll into hub-and-spoke navigation with sub-pages, and (3) polishing the availability editor with card-based styling and a "Copy to all" feature.

## 1. Login Page Redesign

### Current State

The sign-in step is embedded as step 1 of `OnboardingScreen`, sharing a progress bar with availability and calendar setup. It shows a plain heading, subtitle, and a full-width Material button.

### Design

The sign-in becomes a **standalone full-screen splash** — its own route, no progress bar.

**Layout (top to bottom):**
- Background: dark gradient (`#1A1625` → `#2D2640`)
- Vertically centered block:
  - Sortd logo in a rounded container (80dp, `rgba(124,58,237,0.15)` background, 20dp corner radius)
  - "Sortd" — 32sp, bold, `#F1F5F9`
  - "Smart scheduling, sorted." — 14sp, medium weight, `#A78BFA`
- Bottom-anchored:
  - Pill-shaped "Sign in with Google" button (white background, 20dp corner radius, Google icon + text, inline width — not full width)
  - "By signing in, you agree to our Privacy Policy" — 11sp, `#555`, with "Privacy Policy" underlined (same color), linking to `https://ameyajoshi19.github.io/task-tracker/privacy-policy.html`

**Navigation change:**
- New route: `SignIn` — shown when user is not authenticated and onboarding is not complete
- On successful sign-in, navigates to onboarding flow (availability → calendars), which no longer includes a sign-in step
- `OnboardingScreen` drops `OnboardingStep.SIGN_IN`; its first step becomes `AVAILABILITY`
- `MainActivity` routing: unauthenticated → `SignIn`, authenticated + onboarding incomplete → `Onboarding`, authenticated + onboarding complete → `TaskList`

### Files Changed

- `OnboardingScreen.kt` — remove `SignInStep` composable and `SIGN_IN` step handling
- `OnboardingViewModel.kt` — remove sign-in state/logic, start at `AVAILABILITY`
- `OnboardingStep.kt` (or enum in ViewModel) — remove `SIGN_IN` entry
- New: `ui/signin/SignInScreen.kt` — standalone sign-in screen
- `Screen.kt` — add `SignIn` route
- `NavGraph.kt` — add `SignIn` destination, update routing logic
- `MainActivity.kt` — update start destination logic

## 2. Settings Restructure

### Current State

`SettingsScreen` is a single vertically-scrolling column containing Account, Availability, Calendars, Background Sync, and Theme sections inline.

### Design

Replace with **hub-and-spoke navigation**: a top-level menu linking to dedicated sub-pages.

**Settings hub:** Five card rows, each with:
- Colored icon in a tinted rounded container (36dp, 10dp corners)
- Title (14sp, medium weight) and dynamic subtitle (11sp, secondary color)
- Chevron indicator

| Section | Icon | Icon Color | Subtitle |
|---------|------|-----------|----------|
| Account | Person | `#A78BFA` on `rgba(124,58,237,0.15)` | User's email |
| Availability | Clock | `#34D399` on `rgba(52,211,153,0.15)` | "X days active" |
| Calendars | Calendar | `#EC4899` on `rgba(236,72,153,0.15)` | "X calendars synced" |
| Background Sync | Refresh | `#F59E0B` on `rgba(245,158,11,0.15)` | Current interval label |
| Theme | Moon | `#8B5CF6` on `rgba(139,92,246,0.15)` | Current mode name |

**Sub-pages:** Each is a full screen with a top bar (back arrow + title) containing the content currently inline in Settings:
- **Account** — signed-in name/email display, sign out button
- **Availability** — polished `AvailabilityEditor` (see Section 3)
- **Calendars** — checkbox list of calendars with conflict description
- **Background Sync** — sync interval dropdown + battery note
- **Theme** — Light/Dark/Auto toggle chips

**Navigation:** New nested routes under Settings:
- `SettingsAccount`, `SettingsAvailability`, `SettingsCalendars`, `SettingsSync`, `SettingsTheme`

### Files Changed

- `SettingsScreen.kt` — rewrite as hub menu; extract each section's content
- `SettingsViewModel.kt` — add computed subtitle values (active day count, synced calendar count)
- New: `ui/settings/AccountScreen.kt`
- New: `ui/settings/AvailabilitySettingsScreen.kt`
- New: `ui/settings/CalendarsScreen.kt`
- New: `ui/settings/SyncScreen.kt`
- New: `ui/settings/ThemeScreen.kt`
- `Screen.kt` — add 5 new routes
- `NavGraph.kt` — add 5 new destinations

## 3. Availability Editor Polish + Copy to All

### Current State

`AvailabilityEditor` renders days as flat rows with toggles, abbreviated day names, and plain text time displays. No visual card separation. No copy functionality.

### Visual Polish

Each day becomes a **card**:
- Background: `#231E30`, border: `1px #3D3455`, corner radius: 12dp
- Header row: toggle + abbreviated day name (Mon, Tue...) in bold 13sp
- Time slots: start/end times in bordered chip containers (background `#1A1625`, border `#3D3455`, 6dp corners) with "to" label between
- "+ Add slot" link below slots for enabled days
- Additional slots show an inline X remove button
- Disabled days: dimmed card (reduced opacity), only toggle + day name visible

### Copy to All

- **Button:** appears on each enabled day's header row, right-aligned. Small pill: copy icon + "Copy to all" text, `rgba(124,58,237,0.1)` background, `#A78BFA` text
- **Behavior:** Replaces all other days' slots with copies of the source day's slots. All target days become enabled with the same time windows.
- **Feedback:** Brief Snackbar confirms the action (e.g., "Copied Mon's schedule to all days")
- **No confirmation dialog** — the action is easily reversible by editing individual days

**Data flow:**
1. User taps "Copy to all" on a day (e.g., Monday)
2. ViewModel calls repository to:
   - Delete all availability slots for the other 6 days
   - Insert copies of Monday's slots for each of the other 6 days (with appropriate `DayOfWeek` values, `enabled = true`)
3. UI updates reactively via the existing `Flow<List<UserAvailability>>` observation

### `AvailabilityEditor` Callback Addition

New callback: `onCopyToAll: (DayOfWeek) -> Unit` — added to the `AvailabilityEditor` composable signature.

### Files Changed

- `AvailabilityEditor.kt` — card-based layout, time chips, copy button, new `onCopyToAll` callback
- `UserAvailabilityRepository.kt` — add `copyToAllDays(sourceDayOfWeek: DayOfWeek)` interface method
- `UserAvailabilityRepositoryImpl.kt` — implement `copyToAllDays`
- `UserAvailabilityDao.kt` — add `deleteByDayOfWeekExcluding(day: DayOfWeek)` query
- `SettingsViewModel.kt` — wire `copyToAllDays` action
- `OnboardingViewModel.kt` — wire `copyToAllDays` action
- `OnboardingScreen.kt` — pass `onCopyToAll` to `AvailabilityEditor`
- `AvailabilitySettingsScreen.kt` (new) — pass `onCopyToAll` to `AvailabilityEditor`

## Testing

- **Login page:** Verify sign-in flow still works end-to-end, privacy policy link opens in browser, loading/error states display correctly
- **Settings navigation:** Verify each sub-page navigates correctly and back button returns to hub, subtitle values are accurate
- **Copy to all:** Verify slots are correctly copied to all days, source day is unchanged, Snackbar appears, UI updates reactively
- **Availability editor:** Verify card styling renders correctly, time pickers still work, add/remove slot functionality unchanged
