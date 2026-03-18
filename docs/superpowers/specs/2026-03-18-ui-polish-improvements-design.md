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
- All interactions on the card are disabled (swipe, tap, complete toggle)
- On success: shimmer stops, normal flow continues (task updates via StateFlow)
- On error: shimmer stops, snackbar displays the error message

**State changes:**
- Add `reschedulingTaskId: Long?` to `TaskListUiState`
- Set it to the task ID when `rescheduleTask()` is called
- Clear it on completion or error
- `SwipeableTaskCard` checks if its task ID matches `reschedulingTaskId` to show shimmer

**Animation implementation:**
- Use `rememberInfiniteTransition` with `animateFloat` for the shimmer offset
- Overlay is a `Box` with `Modifier.matchParentSize()` inside the card, using `Brush.linearGradient` with animated offset
- Card alpha remains full (no dimming) — the shimmer is the only visual change

### Files Modified
- `TaskListViewModel.kt` — add `reschedulingTaskId` state, set/clear around `rescheduleTask()`
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
- Background: `SortdColors.Dark.card` (`#231E30`) — the elevated card surface color
- Border: `SortdColors.deadlineWarning.copy(alpha = 0.2f)`, 1dp width
- Corner radius: 14dp
- Internal padding: 12dp
- Cards inside use the same `SwipeableTaskCard` but are visually nested within the container

**Spacing:**
- 16dp gap after the container before the first quadrant section (up from 8dp)

**Implementation approach:**
- In the `LazyColumn`, replace the individual Due Today `item` calls with a single `item` block that renders the container `Column` with the header and cards inside
- Since swipe-to-dismiss needs to work inside the container, the cards remain individual composables within the container's `Column`
- Note: This moves the due-today cards out of `LazyColumn`'s lazy rendering, but since due-today tasks are typically few (1-3), this is acceptable

### Files Modified
- `TaskListScreen.kt` — restructure Due Today section into a container `Column` within a single `item`

---

## 4. List View Header — "All Scheduled"

### Problem
When toggling to the list/weekly view (via the grid icon in the top-right), the screen shows tasks from all days but the header still displays the currently selected date and the day-of-week selector, which is misleading.

### Design
When in `WEEKLY` (list) view mode, update the header to reflect the all-tasks context.

**TopAppBar title:**
- `DAILY` mode: shows formatted date (with new bold styling from change #2)
- `WEEKLY` mode: shows "All Scheduled" in the same `headlineMedium` style

**Day-of-week selector row:**
- `DAILY` mode: visible with pill-styled day name and chevron navigation
- `WEEKLY` mode: hidden entirely (the row is not rendered)

**Toggle icon behavior (already correct):**
- `DAILY` → shows `CalendarViewWeek` icon (switch to list)
- `WEEKLY` → shows `CalendarViewDay` icon (switch to daily)

### Files Modified
- `ScheduleScreen.kt` — conditional title text and conditional visibility of day selector row based on `viewMode`

---

## Summary of All Files Modified

| File | Changes |
|------|---------|
| `TaskListViewModel.kt` | Add `reschedulingTaskId` state for shimmer |
| `TaskListScreen.kt` | Shimmer overlay on card, Due Today container |
| `ScheduleScreen.kt` | Typography polish, day pill, list view header |

No new files created. No architectural changes. All modifications are within existing UI layer files.
