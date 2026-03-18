# UI Polish Improvements Design Spec

**Date:** 2026-03-18
**Scope:** 4 focused UI improvements to task list, calendar view, and swipe-to-reschedule

---

## 1. Reschedule Loading — Card Shimmer Animation

### Problem
When a user swipes right to reschedule a task, the scheduling algorithm runs asynchronously but there is no visual feedback on the card itself. The user has no indication that anything is happening between the swipe and the result (navigation or error snackbar).

### Design
After the swipe completes and `rescheduleTask()` is called, the swiped card enters a "rescheduling" state with a purple shimmer overlay animation.

**Behavior:**
- Card displays a horizontal shimmer gradient (`transparent → SortdColors.accent at 20% alpha → transparent`) that sweeps left-to-right on a 1.5s infinite loop
- All interactions on the card are disabled during shimmer state:
  - `SwipeToDismissBox`: set `enableDismissFromStartToEnd = false` and `enableDismissFromEndToStart = false`
  - `TaskCard`: wrap `onClick` and `onComplete` callbacks with a guard that checks rescheduling state (pass no-op lambdas when rescheduling)
- On success: shimmer stops, normal flow continues (task updates via StateFlow)
- On error: shimmer stops, snackbar displays the error message
- Shimmer respects system "reduce motion" accessibility setting — when reduced motion is enabled, show a static semi-transparent accent overlay instead of the animated shimmer

**State changes:**
- Add `reschedulingTaskIds: Set<Long>` to `TaskListUiState` (supports concurrent reschedules)
- Add task ID to the set when `rescheduleTask()` is called
- Remove from set on completion or error
- `SwipeableTaskCard` checks if its task ID is in `reschedulingTaskIds` to show shimmer

**Animation implementation:**
- Use `rememberInfiniteTransition` with `animateFloat` for the shimmer offset
- Overlay is a `Box` with `Modifier.matchParentSize()` inside the card, using `Brush.linearGradient` with animated offset
- Card alpha remains full (no dimming) — the shimmer is the only visual change

### Files Modified
- `TaskListViewModel.kt` — add `reschedulingTaskIds` state, add/remove around `rescheduleTask()`
- `TaskListScreen.kt` — pass rescheduling state to `SwipeableTaskCard`, add shimmer overlay composable

---

## 2. Calendar View — Typography Polish

### Problem
The date header and day-of-week selector in the calendar/schedule screen use default Material typography weights that don't match the bold, dark-themed aesthetic of the rest of the app.

### Design
Increase visual weight of the date and style the day-of-week as an accent pill.

**Date in TopAppBar:**
- Style: `headlineMedium` (22sp, SemiBold) — up from the default `titleMedium`
- Color: `MaterialTheme.colorScheme.onSurface` (unchanged)

**Day-of-week selector:**
- Text wrapped in a pill-shaped container:
  - Background: `SortdColors.accent.copy(alpha = 0.15f)`
  - Border: `SortdColors.accent.copy(alpha = 0.3f)`, 1dp width
  - Corner radius: 20dp
  - Horizontal padding: 20dp, vertical: 6dp
- Text style: `labelLarge` (14sp, Medium weight)
- Text color: `SortdColors.accentLight`

### Files Modified
- `ScheduleScreen.kt` — update `TopAppBar` title style, wrap day-of-week `Text` in pill container

---

## 3. Due Today — Distinct Container

### Problem
The "Due Today" section visually blends into the quadrant sections below it. The header has a subtle red background but the task cards beneath it look identical to quadrant task cards, creating no clear visual boundary.

### Design
Wrap the entire Due Today section (header + all task cards) in a distinct container.

**Container styling:**
- Background: `MaterialTheme.colorScheme.surfaceVariant` — theme-aware card surface (maps to `SortdColors.Dark.card` in dark mode, `SortdColors.Light.card` in light mode)
- Border: `SortdColors.deadlineWarning.copy(alpha = 0.2f)`, 1dp width
- Corner radius: 14dp
- Internal padding: 12dp
- Cards inside use the same `SwipeableTaskCard` but are visually nested within the container

**Spacing:**
- 16dp gap after the container before the first quadrant section (up from 8dp)

**Implementation approach:**
- In the `LazyColumn`, replace the individual Due Today `item` calls with a single `item` block that renders the container `Column` with the header and cards inside
- Since swipe-to-dismiss needs to work inside the container, the cards remain individual composables within the container's `Column`
- Note: This moves the due-today cards out of `LazyColumn`'s lazy rendering, but since due-today tasks are typically few (1-3), this is acceptable. Use `key()` blocks inside the inner `Column` to help Compose's diffing.

### Files Modified
- `TaskListScreen.kt` — restructure Due Today section into a container `Column` within a single `item`

---

## 4. List View Header — "All Scheduled"

### Problem
When toggling to the list view (via the grid icon in the top-right), the screen shows tasks across multiple days but the header still displays the currently selected date and the day-of-week selector, which is misleading.

### Design
Rename the `WEEKLY` view mode to `ALL` to better reflect its purpose, and update both UI and data loading.

**View mode rename:**
- `ViewMode.WEEKLY` → `ViewMode.ALL` (enum value rename in `ScheduleViewModel.kt`)

**TopAppBar title:**
- `DAILY` mode: shows formatted date (with new bold styling from change #2)
- `ALL` mode: shows "All Scheduled" in the same `headlineMedium` style

**Day-of-week selector row:**
- `DAILY` mode: visible with pill-styled day name and chevron navigation
- `ALL` mode: hidden entirely (the row is not rendered)

**Data loading change:**
- In `ALL` mode, `loadSchedule()` fetches all `CONFIRMED`/`COMPLETED` blocks without time-range filtering (currently it filters to a single week). This matches the user expectation that the list view shows everything scheduled.

**Toggle icon behavior (already correct):**
- `DAILY` → shows `CalendarViewWeek` icon (switch to list)
- `ALL` → shows `CalendarViewDay` icon (switch to daily)

### Files Modified
- `ScheduleScreen.kt` — conditional title text and conditional visibility of day selector row based on `viewMode`
- `ScheduleViewModel.kt` — rename `WEEKLY` to `ALL`, update `loadSchedule()` to fetch all blocks in `ALL` mode

---

## Summary of All Files Modified

| File | Changes |
|------|---------|
| `TaskListViewModel.kt` | Add `reschedulingTaskIds` state for shimmer |
| `TaskListScreen.kt` | Shimmer overlay on card, Due Today container |
| `ScheduleScreen.kt` | Typography polish, day pill, list view header |
| `ScheduleViewModel.kt` | Rename `WEEKLY` → `ALL`, update data loading |

No new files created. No architectural changes. All modifications are within existing UI layer files.
