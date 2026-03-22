# Availability Slots, Task Tags, and Home Screen Revamp — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the availability system with named slots, add colored task tags with filtering, reorder tasks by scheduled time, and restructure the home screen around a "Today" view with a navigation drawer.

**Architecture:** New `AvailabilitySlot` entity replaces `UserAvailability`. New `Tag` entity with FK on `Task`. Navigation drawer controls view switching (Today / All Tasks / Tag filter). Sorting uses a new `ScheduledTimeComparator`. Room migration v4 → v5.

**Tech Stack:** Kotlin, Room (migration v4→v5), Hilt DI, Jetpack Compose (Material 3), Google Truth (tests)

**Spec:** `docs/superpowers/specs/2026-03-21-availability-tags-ordering.md`

---

## File Structure

### New Files

| File | Responsibility |
|---|---|
| `domain/model/AvailabilitySlot.kt` | Domain model for named availability slots + `AvailabilitySlotType` enum |
| `domain/model/Tag.kt` | Domain model for colored tags |
| `domain/repository/TagRepository.kt` | Repository interface for Tag CRUD |
| `domain/scheduler/ScheduledTimeComparator.kt` | Comparator: scheduled tasks first (by time asc), then unscheduled |
| `data/local/entity/AvailabilitySlotEntity.kt` | Room entity for `availability_slots` table |
| `data/local/entity/TagEntity.kt` | Room entity for `tags` table |
| `data/local/dao/AvailabilitySlotDao.kt` | Room DAO for availability slots |
| `data/local/dao/TagDao.kt` | Room DAO for tags |
| `data/repository/AvailabilitySlotRepositoryImpl.kt` | Repository implementation (replaces `UserAvailabilityRepositoryImpl`) |
| `data/repository/TagRepositoryImpl.kt` | Repository implementation for tags |
| `ui/components/AvailabilitySlotSelector.kt` | Compose chip row for selecting slot on task edit |
| `ui/components/TagSelector.kt` | Compose dropdown for tag selection + inline creation |
| `ui/components/TagChip.kt` | Small colored chip composable for tag display |
| `ui/components/NavigationDrawer.kt` | Modal navigation drawer with Today/All/Tags sections |
| `ui/tasklist/TodayView.kt` | Today view composable (Overdue / Today / Upcoming / Completed sections) |
| `ui/tasklist/TagFilterView.kt` | Tag-filtered view composable (quadrant groups, filtered by tag) |
| `test/.../domain/scheduler/ScheduledTimeComparatorTest.kt` | Unit tests for the new comparator |

### Modified Files

| File | Changes |
|---|---|
| `domain/model/Task.kt` | Add `availabilitySlot: AvailabilitySlotType?` and `tagId: Long?` fields |
| `domain/model/TaskWithScheduleInfo.kt` | Add `tagName: String?`, `tagColor: Long?`, `availabilitySlot: AvailabilitySlotType?` |
| `domain/model/UserAvailability.kt` | Deprecated / removed (replaced by AvailabilitySlot) |
| `domain/repository/UserAvailabilityRepository.kt` | Replaced by new AvailabilitySlot repository interface |
| `domain/scheduler/SlotFinder.kt` | Accept `AvailabilitySlot` list instead of `UserAvailability`. Filter by task's `availabilitySlot` type. |
| `domain/scheduler/TaskScheduler.kt` | Pass task's slot preference to SlotFinder |
| `domain/validation/TaskValidator.kt` | Validate `availabilitySlot` (if set, at least one day must be enabled for that slot type) |
| `data/local/entity/TaskEntity.kt` | Add `availabilitySlot` and `tagId` columns, update `toDomain`/`fromDomain` |
| `data/local/entity/UserAvailabilityEntity.kt` | Removed (replaced by AvailabilitySlotEntity) |
| `data/local/dao/TaskDao.kt` | Update `observeAllWithNextBlock` query to JOIN with tags table; add tag-filtered queries |
| `data/local/dao/UserAvailabilityDao.kt` | Removed (replaced by AvailabilitySlotDao) |
| `data/local/TaskTrackerDatabase.kt` | Migration v4→v5, update entity list, add new DAOs |
| `data/local/converter/Converters.kt` | Add `AvailabilitySlotType` converter |
| `data/repository/UserAvailabilityRepositoryImpl.kt` | Removed (replaced by AvailabilitySlotRepositoryImpl) |
| `data/repository/TaskRepositoryImpl.kt` | Update mappings for new fields |
| `ui/tasklist/TaskListScreen.kt` | Major refactor: add drawer, Today view as default, view switching |
| `ui/tasklist/TaskListViewModel.kt` | Add view state (Today/All/TagFilter), tag list, filtering logic, today/upcoming/overdue queries |
| `ui/taskedit/TaskEditScreen.kt` | Add AvailabilitySlotSelector and TagSelector fields |
| `ui/taskedit/TaskEditViewModel.kt` | Handle new fields, load tags, create tags inline |
| `ui/settings/AvailabilitySettingsScreen.kt` | Complete rewrite for 3-slot expandable sections |
| `ui/settings/AvailabilitySettingsViewModel.kt` | Use AvailabilitySlot repository instead of UserAvailability |
| `ui/components/TaskCard.kt` | Add tag chip display |
| `ui/navigation/NavGraph.kt` | No route changes needed (drawer is within TaskList screen) |
| `di/RepositoryModule.kt` (or equivalent Hilt module) | Bind new repository implementations |

---

## Step 1: Domain Models and Enums

- [ ] Create `AvailabilitySlotType` enum in `domain/model/AvailabilitySlot.kt` with values: `BEFORE_WORK`, `DURING_WORK`, `AFTER_WORK`
- [ ] Create `AvailabilitySlot` data class with fields: `id`, `slotType`, `dayOfWeek`, `startTime`, `endTime`, `enabled`
- [ ] Create `Tag` data class in `domain/model/Tag.kt` with fields: `id`, `name`, `color`, `createdAt`
- [ ] Add `availabilitySlot: AvailabilitySlotType?` field to `Task` (default null = any slot)
- [ ] Add `tagId: Long?` field to `Task` (default null = no tag)
- [ ] Update `TaskWithScheduleInfo` to include `tagName: String?`, `tagColor: Long?`, `availabilitySlot: AvailabilitySlotType?`
- [ ] Remove or deprecate `UserAvailability` domain model

**Tests:** None needed — pure data classes.

---

## Step 2: Room Entities, DAOs, and Migration

- [ ] Create `AvailabilitySlotEntity` with `@Entity(tableName = "availability_slots")`, unique index on `(slotType, dayOfWeek)`
- [ ] Create `AvailabilitySlotDao` with: `observeAll()`, `observeBySlotType()`, `getBySlotType()`, `getEnabled()`, `update()`, `insertAll()`
- [ ] Create `TagEntity` with `@Entity(tableName = "tags")`, unique index on `name`
- [ ] Create `TagDao` with: `observeAll()`, `getAll()`, `getById()`, `insert()`, `delete()`
- [ ] Add `availabilitySlot: String?` column to `TaskEntity` (stored as enum name string)
- [ ] Add `tagId: Long?` column to `TaskEntity`
- [ ] Update `TaskEntity.toDomain()` and `TaskEntity.fromDomain()` for new fields
- [ ] Add `AvailabilitySlotType` converter to `Converters.kt`
- [ ] Update `TaskDao.observeAllWithNextBlock` query to LEFT JOIN with `tags` to include `tagName` and `tagColor`
- [ ] Add `TaskDao.observeByTagId(tagId: Long)` query
- [ ] Write Room migration v4 → v5:
  - Drop `user_availability` table
  - Create `availability_slots` table with 21 pre-populated rows (3 types x 7 days, all disabled)
  - Create `tags` table
  - Add `availability_slot` column to `tasks` (nullable, default null)
  - Add `tag_id` column to `tasks` (nullable, default null)
- [ ] Update `TaskTrackerDatabase`: version = 5, add new entities, add migration, expose new DAOs
- [ ] Remove `UserAvailabilityEntity`, `UserAvailabilityDao`

**Tests:**
- [ ] Test migration v4 → v5 (verify table creation, data preserved in tasks table, old table dropped)

---

## Step 3: Repositories and DI

- [ ] Create `AvailabilitySlotRepository` interface in domain layer (replaces `UserAvailabilityRepository`)
  - `observeAll(): Flow<List<AvailabilitySlot>>`
  - `getEnabled(): List<AvailabilitySlot>`
  - `getBySlotType(type: AvailabilitySlotType): List<AvailabilitySlot>`
  - `update(slot: AvailabilitySlot)`
- [ ] Create `AvailabilitySlotRepositoryImpl` in data layer
- [ ] Create `TagRepository` interface in domain layer
  - `observeAll(): Flow<List<Tag>>`
  - `getAll(): List<Tag>`
  - `insert(tag: Tag): Long`
  - `delete(tag: Tag)`
- [ ] Create `TagRepositoryImpl` in data layer
- [ ] Update `TaskRepositoryImpl` to handle new fields in entity mapping
- [ ] Remove `UserAvailabilityRepository` and `UserAvailabilityRepositoryImpl`
- [ ] Update Hilt DI module: bind `AvailabilitySlotRepository`, `TagRepository`, remove old `UserAvailabilityRepository` binding

**Tests:** None — thin wrappers over DAOs.

---

## Step 4: Scheduler Updates

- [ ] Update `SlotFinder` to accept `List<AvailabilitySlot>` instead of `List<UserAvailability>`
  - When filtering for a specific task, accept an optional `AvailabilitySlotType?` parameter
  - If non-null, only use `AvailabilitySlot` entries matching that type
  - If null, use all enabled slots (current behavior)
- [ ] Update `TaskScheduler.schedule()` to pass each task's `availabilitySlot` to `SlotFinder`
- [ ] Create `ScheduledTimeComparator` in `domain/scheduler/`:
  - Primary sort: tasks with `nextBlockStart` (non-null) before tasks without
  - Secondary sort for scheduled tasks: `nextBlockStart` ascending
  - Tertiary sort for unscheduled tasks: delegate to `TaskPriorityComparator`
- [ ] Update all call sites of `SlotFinder` (TaskEditViewModel, TaskScheduler, SyncScheduler) to use new `AvailabilitySlot` types

**Tests:**
- [ ] `ScheduledTimeComparatorTest`: scheduled before unscheduled, ascending by time, unscheduled fallback to priority
- [ ] Update existing `SlotFinder` tests to use `AvailabilitySlot` instead of `UserAvailability`
- [ ] Update existing `TaskScheduler` tests for new input types

---

## Step 5: Availability Settings UI

- [ ] Rewrite `AvailabilitySettingsScreen`:
  - Three expandable cards: "Before Work", "During Work", "After Work"
  - Each card expands to show 7 day rows
  - Each day row: day name, enabled switch, start time picker, end time picker
  - "Copy to all days" button in each card's header
  - Save on change (debounced)
  - Use `MaterialTheme.colorScheme` tokens throughout — `surfaceVariant` for card backgrounds, theme-aware text colors. No hardcoded colors.
- [ ] Rewrite `AvailabilitySettingsViewModel`:
  - Observe `AvailabilitySlotRepository.observeAll()`
  - Group slots by type for UI
  - `updateSlot()` function to persist changes
  - `copyToAllDays(slotType, sourceDay)` function

**Tests:** Manual — UI-only changes.

---

## Step 6: Tag System UI (Task Edit)

- [ ] Create `TagChip` composable: small chip with colored background and tag name text. Use contrast-aware text color (white on dark backgrounds, dark on light) computed from tag color luminance. Must render correctly in both light and dark themes.
- [ ] Create `TagSelector` composable:
  - ExposedDropdownMenu showing existing tags as colored chips
  - "No tag" option at top
  - "+ Add new tag" option at bottom
  - Inline creation: text field + color picker (row of 8 colored circles) + "Create" button
  - On create: insert tag via ViewModel, auto-select it
- [ ] Add `TagSelector` to `TaskEditScreen` (below title/description, above duration)
- [ ] Update `TaskEditViewModel`:
  - Load tags from `TagRepository.observeAll()`
  - `selectedTagId: Long?` state
  - `createTag(name: String, color: Long)` function
  - Pass `tagId` when saving task

**Tests:** Manual — UI-only changes.

---

## Step 7: Task Card Tag Display

- [ ] Update `TaskCard` / `SwipeableTaskCard` composable:
  - If task has a tag, show `TagChip` after the quadrant dot and before the title
  - Use `tagName` and `tagColor` from `TaskWithScheduleInfo`
- [ ] Update `AvailabilitySlotSelector` composable (chip row for task edit):
  - Show on task edit form below Day Preference
  - 4 chips: "Before Work", "During Work", "After Work", "Any"
  - Only show chips for slot types that have at least one enabled day
- [ ] Add `AvailabilitySlotSelector` to `TaskEditScreen`
- [ ] Update `TaskEditViewModel` to track `selectedAvailabilitySlot` state

**Tests:** Manual — UI-only changes.

---

## Step 8: Navigation Drawer

- [ ] Create `NavigationDrawer` composable:
  - Modal drawer (ModalNavigationDrawer from Material 3)
  - Header section with app logo
  - "Today" item (with calendar-today icon)
  - "All Tasks" item (with list icon)
  - Divider
  - "Tags" section header
  - List of tags (colored dot + name), loaded from ViewModel
  - Selected item highlighted
  - Use `NavigationDrawerItem` from Material 3 (handles theme-aware selection automatically). All colors from `MaterialTheme.colorScheme`.
- [ ] Update `TaskListScreen`:
  - Wrap content in `ModalNavigationDrawer`
  - Replace top-left logo with hamburger icon that opens drawer
  - Add view title to center of top bar
  - Track selected view in ViewModel: `Today`, `AllTasks`, `TagFilter(tagId)`
- [ ] Update `TaskListViewModel`:
  - Add `sealed class ViewMode { Today, AllTasks, TagFilter(tagId, tagName) }`
  - `currentViewMode: StateFlow<ViewMode>` (default: Today)
  - `tags: StateFlow<List<Tag>>` from TagRepository
  - `setViewMode(mode: ViewMode)` function

**Tests:** Manual — UI-only changes.

---

## Step 9: Today View

- [ ] Create `TodayView` composable:
  - **Overdue section**: Filter tasks where (`nextBlockStart` < startOfToday OR `deadline` < startOfToday) AND status != COMPLETED. Header uses `MaterialTheme.colorScheme.error` color with count badge. Sorted by scheduled time ascending.
  - **Today section**: Filter tasks where `nextBlockStart` is within today, OR `deadline` is today (even if unscheduled). Header uses `MaterialTheme.colorScheme.primary`. Sorted by scheduled time ascending, unscheduled deadline tasks at end.
  - **Upcoming section**: Filter tasks where `nextBlockStart` is within tomorrow. Header uses `MaterialTheme.colorScheme.tertiary` or `outline`. Sorted by scheduled time ascending.
  - **Completed section**: Tasks completed today (check `updatedAt` within today and status == COMPLETED). Collapsed by default. Sorted by `updatedAt` descending.
  - Empty sections hidden. Each section shows count badge.
  - Same `SwipeableTaskCard` for task items (tap to edit, swipe to delete/reschedule, checkbox to complete)
  - **Theming**: All colors from `MaterialTheme.colorScheme`. Match existing card styles, spacing, and typography patterns. Must render correctly in both light and dark themes.
- [ ] Update `TaskListViewModel`:
  - Compute `overdueTask`, `todayTasks`, `upcomingTasks`, `completedTodayTasks` from the full task list based on `nextBlockStart`, `deadline`, and `status`
  - Use `ScheduledTimeComparator` for ordering within sections
  - Use `updatedAt` descending for completed section

**Tests:** Manual — UI-only changes.

---

## Step 10: All Tasks View and Tag Filter View

- [ ] Refactor existing quadrant-based task list into `AllTasksView` composable:
  - Extract from current `TaskListScreen` content
  - Remove "Due Today" section (now in Today view)
  - Apply `ScheduledTimeComparator` within each quadrant group
  - Completed section at bottom, ordered by `updatedAt` descending
- [ ] Create `TagFilterView` composable:
  - Filter tasks by selected `tagId`
  - Group by quadrant (same layout as AllTasksView)
  - Apply `ScheduledTimeComparator` within each group
  - Show tag name + color in a header or top bar subtitle
  - Completed section at bottom (filtered by tag)
- [ ] Wire up view switching in `TaskListScreen`:
  - When `viewMode == Today` → show `TodayView`
  - When `viewMode == AllTasks` → show `AllTasksView`
  - When `viewMode == TagFilter(tagId)` → show `TagFilterView`
- [ ] Ensure FAB (add task) is visible on all views
- [ ] Ensure pull-to-refresh works on all views

**Tests:** Manual — UI-only changes.

---

## Step 11: Integration and Cleanup

- [ ] Update `TaskEditViewModel` scheduling flow to pass `availabilitySlot` to `SlotFinder`
- [ ] Update `SyncScheduler` / background sync to use `AvailabilitySlot` instead of `UserAvailability`
- [ ] Update onboarding flow: if user has no availability slots enabled, prompt them to set up availability after first task creation
- [ ] Delete removed files: `UserAvailability.kt`, `UserAvailabilityEntity.kt`, `UserAvailabilityDao.kt`, `UserAvailabilityRepository.kt`, `UserAvailabilityRepositoryImpl.kt`
- [ ] Run `./gradlew test` — fix any compilation errors from removed types
- [ ] Run `./gradlew assembleDebug` — verify clean build
- [ ] Manual smoke test: create slots, create tags, create tasks with slot + tag, verify Today view, drawer navigation, tag filtering, scheduling respects slot selection

---

## Dependency Order

```
Step 1 (Domain Models)
  ↓
Step 2 (Room Entities + Migration)
  ↓
Step 3 (Repositories + DI)
  ↓
Step 4 (Scheduler Updates)
  ↓
Step 5 (Availability Settings UI)  ←── can parallel with Step 6
Step 6 (Tag System UI)             ←── can parallel with Step 5
  ↓
Step 7 (Task Card + Slot Selector)
  ↓
Step 8 (Navigation Drawer)
  ↓
Step 9 (Today View)   ←── can parallel with Step 10
Step 10 (All Tasks + Tag Filter Views)  ←── can parallel with Step 9
  ↓
Step 11 (Integration + Cleanup)
```

Steps 1–4 are sequential (each depends on the previous). Steps 5–6 can be parallelized. Steps 9–10 can be parallelized. Step 11 is the final integration pass.
