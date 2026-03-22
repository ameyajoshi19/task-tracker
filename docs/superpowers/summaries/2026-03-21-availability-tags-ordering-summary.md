# Availability Slots, Task Tags, and Home Screen Revamp — Summary

**Date:** 2026-03-21
**Spec:** `docs/superpowers/specs/2026-03-21-availability-tags-ordering.md`
**Plan:** `docs/superpowers/plans/2026-03-21-availability-tags-ordering.md`
**Status:** Complete — all features implemented, reviewed, and QA-passed (107/107 tests)

---

## What Was Built

Four interconnected features that revamp how tasks are organized, scheduled, and displayed:

### Feature 1: Availability Slots Revamp

**Before:** A single configurable time window per day of the week (e.g., Monday 9am–5pm), stored in the `user_availability` table. Tasks were scheduled within whatever window was active for that day.

**After:** Three named time slots per day — **Before Work**, **During Work**, and **After Work** — each independently configurable with per-day time ranges and enable/disable toggles. Tasks are assigned to exactly one slot (or "Any"), and the scheduler only considers that slot's windows when placing the task.

**Key changes:**
- New `AvailabilitySlot` domain model and `AvailabilitySlotType` enum (BEFORE_WORK, DURING_WORK, AFTER_WORK)
- New `availability_slots` Room table (21 pre-populated rows: 3 types x 7 days) replaces `user_availability`
- `Task` model gains `availabilitySlot: AvailabilitySlotType?` field (null = any slot)
- `SlotFinder` filters availability windows by task's slot preference
- `TaskScheduler` passes slot preference through to SlotFinder — core best-fit algorithm unchanged
- Availability Settings screen rewritten with 3 expandable sections, 7 day rows each, "Copy to all days" action
- Task edit form gains an availability window chip selector (Before Work / During Work / After Work / Any)
- Database migration v4 → v5 drops old table, creates new one fresh (no data migration)

### Feature 2: Task Tags

**Before:** No tagging or categorization system. Tasks were only organized by Eisenhower quadrant.

**After:** User-defined colored tags that can be assigned to tasks and used for filtering via the navigation drawer.

**Key changes:**
- New `Tag` domain model (id, name, color as ARGB Long, createdAt)
- New `tags` Room table with unique index on name
- `Task` model gains `tagId: Long?` field (single tag per task; data model supports future many-to-many via junction table)
- `TaskWithScheduleInfo` gains `tagName` and `tagColor` for display without extra queries
- `TaskDao` queries updated with LEFT JOIN to tags table
- Tag selector on task edit form: dropdown with existing tags, "No tag" option, inline "+ Add new tag" with text field + 8-color palette picker
- `TagChip` composable with contrast-aware text (luminance-based white/black text on colored background)
- Tag chips displayed on task cards after quadrant dot, before title

### Feature 3: Task Ordering by Scheduled Time

**Before:** Tasks within each quadrant section were sorted by the `TaskPriorityComparator` (quadrant → deadline → createdAt). No consideration of scheduled time.

**After:** Tasks are ordered by scheduled time ascending within every section across all views. Unscheduled tasks appear after scheduled tasks.

**Key changes:**
- New `ScheduledTimeComparator`: scheduled tasks first (by `nextBlockStart` ascending), unscheduled tasks second (fallback to `TaskPriorityComparator`)
- Applied consistently in Today view, All Tasks view, and Tag Filter view
- Completed sections ordered by `updatedAt` descending (most recently completed first)
- Unit tests covering all ordering scenarios

### Feature 4: Home Screen Revamp & Navigation Drawer

**Before:** Single home screen with quadrant-based grouping (Now / Next / Soon / Later) plus a "Due Today" section and Completed section.

**After:** Three distinct views accessible via a modal navigation drawer, with "Today" as the default landing page.

**Key changes:**

**Navigation Drawer:**
- Opens via hamburger icon (replaces app logo in top-left)
- Structure: Today (default) → All Tasks → Tags section (colored dots + names)
- View title displayed in center of top bar
- Calendar + Settings icons retained in top-right

**Today View (default):**
- **Overdue section**: Past-scheduled or past-deadline uncompleted tasks (error/red header)
- **Today section**: Tasks scheduled today + tasks with deadline today (even if unscheduled)
- **Upcoming section**: Tasks scheduled for tomorrow
- **Completed section**: Tasks completed today (collapsed by default, descending by completion time)
- Empty sections are hidden; each shows count badge

**All Tasks View (catch-all):**
- Preserved quadrant-based layout (Now / Next / Soon / Later)
- Every task in the system appears here regardless of schedule, deadline, or tag status
- "Due Today" section removed (now part of Today view)
- Completed section shows all completed tasks

**Tag Filter View:**
- Shows only tasks with the selected tag
- Groups by quadrant (same layout as All Tasks)
- Completed section filtered by tag
- Header shows tag name with colored indicator

---

## Database Changes

| Change | Details |
|--------|---------|
| Version | v4 → v5 |
| New table | `availability_slots` (21 rows, 3 types x 7 days) |
| New table | `tags` (user-created, unique name index) |
| New columns | `tasks.availability_slot` (String?, nullable), `tasks.tag_id` (Long?, nullable) |
| Dropped table | `user_availability` (replaced by availability_slots) |
| Migration | Fresh start — no data migration from old availability system |

## Files Added

| File | Purpose |
|------|---------|
| `domain/model/AvailabilitySlot.kt` | Domain model + AvailabilitySlotType enum |
| `domain/model/Tag.kt` | Tag domain model |
| `domain/repository/TagRepository.kt` | Tag repository interface |
| `domain/repository/AvailabilitySlotRepository.kt` | Availability slot repository interface |
| `domain/scheduler/ScheduledTimeComparator.kt` | Scheduled-time-first comparator |
| `data/local/entity/AvailabilitySlotEntity.kt` | Room entity for availability_slots |
| `data/local/entity/TagEntity.kt` | Room entity for tags |
| `data/local/dao/AvailabilitySlotDao.kt` | DAO for availability slots |
| `data/local/dao/TagDao.kt` | DAO for tags |
| `data/repository/AvailabilitySlotRepositoryImpl.kt` | Availability slot repository impl |
| `data/repository/TagRepositoryImpl.kt` | Tag repository impl |
| `ui/components/AvailabilitySlotSelector.kt` | Chip row for slot selection on task edit |
| `ui/components/TagSelector.kt` | Dropdown + inline tag creation |
| `ui/components/TagChip.kt` | Contrast-aware colored tag chip |
| `ui/components/NavigationDrawer.kt` | Modal navigation drawer |
| `ui/tasklist/TodayView.kt` | Today view (Overdue/Today/Upcoming/Completed) |
| `ui/tasklist/AllTasksView.kt` | All Tasks quadrant view (catch-all) |
| `ui/tasklist/TagFilterView.kt` | Tag-filtered quadrant view |
| `test/.../ScheduledTimeComparatorTest.kt` | Unit tests for comparator |

## Files Modified

| File | Changes |
|------|---------|
| `domain/model/Task.kt` | Added `availabilitySlot`, `tagId` fields |
| `domain/model/TaskWithScheduleInfo.kt` | Added `tagName`, `tagColor`, `availabilitySlot` |
| `domain/scheduler/SlotFinder.kt` | Accepts AvailabilitySlot list + slotType filter param |
| `domain/scheduler/TaskScheduler.kt` | Passes task's slot preference to SlotFinder |
| `domain/validation/TaskValidator.kt` | Validates availability slot selection |
| `data/local/entity/TaskEntity.kt` | Added `availabilitySlot`, `tagId` columns |
| `data/local/dao/TaskDao.kt` | JOIN with tags, observeByTagId query |
| `data/local/TaskTrackerDatabase.kt` | Version 5, migration 4→5, new entities/DAOs |
| `data/local/converter/Converters.kt` | AvailabilitySlotType converter |
| `data/repository/TaskRepositoryImpl.kt` | Updated mappings for new fields |
| `ui/tasklist/TaskListScreen.kt` | Drawer integration, view switching, hamburger menu, view title |
| `ui/tasklist/TaskListViewModel.kt` | ViewMode sealed class, Today/AllTasks/TagFilter state, tag list, section computation |
| `ui/taskedit/TaskEditScreen.kt` | Added TagSelector + AvailabilitySlotSelector |
| `ui/taskedit/TaskEditViewModel.kt` | Tag CRUD, availability slot selection, enabled slot types |
| `ui/settings/AvailabilitySettingsScreen.kt` | Complete rewrite for 3-slot expandable UI |
| `ui/settings/AvailabilitySettingsViewModel.kt` | Uses AvailabilitySlotRepository |
| `ui/components/TaskCard.kt` | Tag chip display |
| `di/RepositoryModule.kt` | New repository bindings |

## Files Removed

| File | Reason |
|------|--------|
| `domain/model/UserAvailability.kt` | Replaced by AvailabilitySlot |
| `domain/repository/UserAvailabilityRepository.kt` | Replaced by AvailabilitySlotRepository |
| `data/local/entity/UserAvailabilityEntity.kt` | Replaced by AvailabilitySlotEntity |
| `data/local/dao/UserAvailabilityDao.kt` | Replaced by AvailabilitySlotDao |
| `data/repository/UserAvailabilityRepositoryImpl.kt` | Replaced by AvailabilitySlotRepositoryImpl |

---

## QA Results

- **107/107 tests passed**
- Full spec compliance verified across all 4 features
- 6 minor non-blocking issues identified (cosmetic/polish)
- Light and dark theme support verified
- UX consistency with existing app patterns confirmed
- Task visibility guarantee confirmed: every task reachable via "All Tasks" catch-all view

## Design Decisions Made During Brainstorming

| Decision | Rationale |
|----------|-----------|
| Preset-only slots (no custom names) | Keeps UX simple; 3 natural time divisions cover most use cases; extensible later |
| Fresh start (no availability migration) | User preferred clean slate over automatic migration |
| One tag per task (extensible to many) | Simplest UX now; data model supports junction table upgrade |
| Single-select tag filtering | Simpler than multi-select; "All Tasks" serves as unfiltered catch-all |
| Unscheduled tasks after scheduled | Scheduled tasks are more actionable; unscheduled fall to bottom naturally |
| Completed section by completion time desc | Most recently completed first is most useful for "what did I just finish" |
| Today view as default home | Gives users an immediate action-oriented view vs. the broader quadrant overview |
