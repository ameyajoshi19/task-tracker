# Availability Slots, Task Tags, and Home Screen Revamp — Design Spec

Three features plus a home screen restructure: (1) replace the per-day availability system with named time slots, (2) add colored task tags with filtering, (3) reorder tasks by scheduled time, and (4) restructure the home screen around a "Today" view with a navigation drawer.

## Feature 1: Availability Slots Revamp

### Overview

Replace the current single-window-per-day availability system with three preset named slots per day: **Before Work**, **During Work**, and **After Work**. Each slot has per-day time ranges. Tasks are assigned to exactly one slot, and the scheduler only considers that slot's windows when placing the task.

### Data Model

#### AvailabilitySlotType (Enum)

| Value | Display Name |
|-------|-------------|
| BEFORE_WORK | Before Work |
| DURING_WORK | During Work |
| AFTER_WORK | After Work |

#### AvailabilitySlot (replaces UserAvailability)

| Field | Type | Notes |
|-------|------|-------|
| id | Long | Auto-generated primary key |
| slotType | AvailabilitySlotType | BEFORE_WORK, DURING_WORK, or AFTER_WORK |
| dayOfWeek | DayOfWeek | MONDAY through SUNDAY |
| startTime | LocalTime | Start of the window |
| endTime | LocalTime | End of the window |
| enabled | Boolean | Whether this slot is active for this day |

Unique constraint: `(slotType, dayOfWeek)` — exactly one entry per slot type per day. 21 rows total (3 slots x 7 days). All rows are pre-created; `enabled` toggles them on/off.

#### Task Changes

| Field | Type | Notes |
|-------|------|-------|
| availabilitySlot | AvailabilitySlotType? | Which slot this task should be scheduled within. Nullable = "any slot" (all enabled slots are valid). |

The existing `dayPreference` field is retained — it controls weekday/weekend filtering independently of the slot selection.

### Scheduler Changes

- **SlotFinder**: When finding available slots for a task, filter `AvailabilitySlot` entries by the task's `availabilitySlot` value. If null, use all enabled slots (current behavior).
- **TaskScheduler**: Pass the task's slot preference through to SlotFinder. No changes to the core best-fit algorithm.

### Migration

- **Database migration** (v4 → v5): Drop the `user_availability` table. Create `availability_slots` table. Pre-populate with 21 rows (all disabled, empty times). Add `availability_slot` column to `tasks` table (nullable, default null).
- **Start fresh**: No data migration from old availability windows. On first launch after update, navigate the user to the availability settings screen to configure their new slots.

### Settings UI

Replace the current `AvailabilitySettingsScreen` with a new layout:

- **Three expandable sections**: "Before Work", "During Work", "After Work"
- Each section expands to show 7 day rows (Mon–Sun)
- Each day row has: enabled toggle, start time picker, end time picker
- **Quick actions**: "Copy to all days" button within each section to duplicate one day's times to all others
- **Validation**: endTime must be after startTime. Slots should not overlap within the same day (warn but allow — scheduler handles overlaps by merging).

### Task Edit UI

- Add an **"Availability Window"** selector below the Day Preference selector
- Display as a row of 3 + 1 option chips: "Before Work", "During Work", "After Work", "Any"
- "Any" is the default (maps to null in the data model)
- Only show chips for slots that have at least one enabled day (hide fully-disabled slots)

---

## Feature 2: Task Tags

### Overview

Add user-defined colored tags to tasks. One tag per task (data model supports future expansion to many-to-many). Tags are created inline during task editing. A navigation drawer enables filtering the task list by tag.

### Data Model

#### Tag

| Field | Type | Notes |
|-------|------|-------|
| id | Long | Auto-generated primary key |
| name | String | Unique, user-defined (e.g., "Work", "Personal") |
| color | Long | ARGB color value stored as Long |
| createdAt | Instant | Auto-set |

#### Task Changes

| Field | Type | Notes |
|-------|------|-------|
| tagId | Long? | Foreign key to Tag. Nullable (task may have no tag). |

A join query `TaskWithScheduleInfo` will include the tag name and color for display.

#### Future: Many-to-Many

When multiple tags per task are needed, add a `task_tags` junction table (`taskId`, `tagId`) and remove the `tagId` column from `tasks`. The current single-tag design keeps things simple while the junction table path is straightforward.

### Default Colors

Provide a palette of 8 preset colors for tag creation (user picks one):
- Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink

### Task Edit UI

- Add a **"Tag"** field below the title/description section
- Displays as a dropdown (ExposedDropdownMenu):
  - Current tag shown as a colored chip (or "No tag" placeholder)
  - Dropdown lists existing tags as colored chips
  - Bottom item: **"+ Add new tag"** — tapping opens an inline row with a text field + color picker (row of 8 color circles) + "Create" button
  - Selecting a tag assigns it; selecting "No tag" clears the assignment
- Tag deletion/editing: Not in scope for this release. Tags can be created and assigned but not renamed or deleted yet.

### Task Card Display

- Show the tag as a small colored chip (background = tag color, text = tag name) on the task card, positioned after the quadrant color dot and before the title.

---

## Feature 3: Task Ordering by Scheduled Time

### Overview

Within each section/group on the task list, tasks are ordered by their scheduled time (`nextBlockStart`) in ascending order. Unscheduled tasks appear after scheduled tasks.

### Sorting Rules

**Within each quadrant section or filtered view:**

1. **Scheduled tasks first**, ordered by `nextBlockStart` ascending (earliest first)
2. **Unscheduled tasks second**, ordered by the existing `TaskPriorityComparator` (quadrant → deadline → createdAt)

**Completed section:**
- Ordered by completion time descending (most recently completed first)
- Requires tracking when a task was marked completed — use the `updatedAt` field (which is set when status changes to COMPLETED)

### Implementation

- Create a new `ScheduledTimeComparator` that sorts by: hasSchedule (true first) → nextBlockStart ascending → fallback to TaskPriorityComparator
- Apply this comparator within each group in every view (Today, All Tasks, Tag filter)

---

## Feature 4: Home Screen Revamp & Navigation Drawer

### Overview

Replace the current quadrant-focused home screen with a **"Today" view** as the default landing page. Add a **navigation drawer** (slides from left) for switching between views and filtering by tags.

### Navigation Drawer Structure

```
[App Logo / Header]
─────────────────────
  Today            ← default, selected on launch
  All Tasks        ← quadrant-based view (current home screen layout)
─────────────────────
  TAGS
  ● Work           ← colored dot + tag name
  ● Personal
  ● (other tags)
─────────────────────
```

- Drawer opens via hamburger icon in top-left (replaces current logo-only top bar)
- Selected item is highlighted
- Drawer closes on item selection

### Today View (Default Home Screen)

Sections displayed in order:

1. **Overdue** (red/warning header): Tasks where `nextBlockStart` < start of today OR `deadline` < start of today, AND status is not COMPLETED. Ordered by scheduled time ascending.

2. **Today**: Tasks where `nextBlockStart` falls within today. Also includes tasks where `deadline` is today even if not yet scheduled (so user sees deadline awareness). Ordered by scheduled time ascending; unscheduled deadline-today tasks appear at the end of this section.

3. **Upcoming**: Tasks where `nextBlockStart` falls tomorrow. Ordered by scheduled time ascending.

4. **Completed** (collapsed by default): Tasks completed today, ordered by completion time descending (most recently completed first).

Each section shows a task count badge. Empty sections are hidden.

### All Tasks View (Catch-All)

The current home screen layout, preserved. **This is the catch-all view — every task in the system appears here** regardless of schedule, deadline, or tag status:
- Groups: Quadrant sections (Now / Next / Soon / Later)
- Within each quadrant: sorted by scheduled time ascending (Feature 3)
- Completed section at the bottom showing **all** completed tasks (descending by completion time)
- "Due Today" section removed (now part of Today view)

### Tag Filter View

When a tag is selected from the drawer:
- Shows only tasks with that tag
- Groups by quadrant (Now / Next / Soon / Later) — same layout as All Tasks
- Within each quadrant: sorted by scheduled time ascending
- Completed section at the bottom (tag-filtered, descending by completion time)
- Header shows tag name + colored indicator

### Top Bar Changes

| Element | Before | After |
|---------|--------|-------|
| Left | App logo | Hamburger menu icon (opens drawer) |
| Center | (none) | View title ("Today", "All Tasks", or tag name) |
| Right | Calendar + Settings icons | Calendar + Settings icons (unchanged) |

### Pull-to-Refresh

Retained on all views. Triggers Google Calendar sync as before.

---

## Data Model Summary (All Changes)

### New Tables

| Table | Purpose |
|-------|---------|
| `availability_slots` | Named time slots per day (replaces `user_availability`) |
| `tags` | User-defined colored tags |

### Modified Tables

| Table | New Columns |
|-------|------------|
| `tasks` | `availability_slot` (String?, maps to AvailabilitySlotType), `tag_id` (Long?, FK to tags) |

### Dropped Tables

| Table | Reason |
|-------|--------|
| `user_availability` | Replaced by `availability_slots` |

### Database Version

v4 → v5 (single migration covering all changes)

---

## Interaction Between Features

- **Availability slots + scheduling**: A task with `availabilitySlot = BEFORE_WORK` is only scheduled within Before Work windows. Combined with `dayPreference`, this means a task with `BEFORE_WORK + WEEKDAY` only gets Before Work slots on Mon–Fri.
- **Tags + drawer**: Tags are purely organizational/filtering — they don't affect scheduling.
- **Ordering + views**: The scheduled-time sort applies uniformly across Today, All Tasks, and Tag filter views within their respective sections.
- **Today view + tags**: The Today view shows all tasks regardless of tag. Tag filtering is a separate view accessed from the drawer.

## Task Visibility Guarantee

**No task should ever be invisible.** The "All Tasks" view is the catch-all — every non-deleted task appears there in its quadrant group, regardless of whether it has a schedule, deadline, or tag. Here's how edge cases are handled:

| Task State | Today View | All Tasks | Tag Filter |
|---|---|---|---|
| No tag, no schedule, no deadline | Not shown | Visible in quadrant (unscheduled, sorted last) | Not shown |
| Has tag, no schedule, no deadline | Not shown | Visible in quadrant | Visible in quadrant |
| Scheduled for next week | Not shown | Visible in quadrant | Visible if tagged |
| Overdue (past schedule/deadline, not completed) | Visible in Overdue section | Visible in quadrant | Visible if tagged |
| Completed yesterday | Not shown (Today only shows today's completions) | Visible in Completed section | Visible in Completed if tagged |
| Completed last week | Not shown | Visible in Completed section | Visible in Completed if tagged |

**Key invariant:** Every active (non-completed) task appears in its quadrant group in "All Tasks". Every completed task appears in the "All Tasks" Completed section. Users can always navigate to "All Tasks" to see everything.

---

## UX and Theming Requirements

### Consistency with Existing App

All new UI components must match the existing app's visual patterns:
- **Cards**: Use the same card styles (elevation, corner radius, padding) as existing `TaskCard` / `SwipeableTaskCard`
- **Chips**: Match the chip style used in the duration picker and quadrant selector (rounded, consistent padding, font size)
- **Spacing**: Follow existing vertical/horizontal spacing conventions between sections, cards, and form fields
- **Typography**: Use `MaterialTheme.typography` tokens (e.g., `titleMedium`, `bodySmall`) consistent with existing screens
- **Interactions**: Swipe-to-delete/reschedule, tap-to-edit, checkbox completion — reuse existing interaction patterns in new views (Today, Tag Filter)

### Light and Dark Theme Support

All new UI must render correctly in both light and dark themes:
- **Use `MaterialTheme.colorScheme` tokens** throughout — never hardcode colors (e.g., use `surface`, `onSurface`, `primary`, `primaryContainer`, `outline`, `error`, etc.)
- **Tag chips**: Tag colors are user-selected, so ensure text on tag chips has sufficient contrast in both themes. Use a contrast-aware text color: white text on dark tag backgrounds, dark text on light tag backgrounds (compute based on luminance of the tag color)
- **Navigation drawer**: Use `NavigationDrawerItem` from Material 3 which handles theme-aware selection highlighting automatically
- **Expandable availability cards**: Use `MaterialTheme.colorScheme.surfaceVariant` for card backgrounds, `onSurfaceVariant` for secondary text
- **Section headers** (Overdue, Today, Upcoming): Use semantic colors — `error` for Overdue, `primary` for Today, `tertiary` or `outline` for Upcoming
- **No hardcoded hex colors** anywhere in new composables — all colors must come from the theme or be derived from theme tokens
