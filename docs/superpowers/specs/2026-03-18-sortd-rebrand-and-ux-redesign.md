# Sortd — Rebrand & UX Redesign

## Overview

Rebrand the app from "Task Tracker" to "Sortd" with a new visual identity, theme system, and polished task creation UX. The app's core scheduling engine and architecture remain unchanged — this spec covers branding, theming, and UI-layer improvements only.

## 1. Branding & Identity

### App Name
- **Sortd** — conveys intelligent sorting of tasks into time slots.

### Wordmark
- Lowercase `sortd.` with a colored accent dot.
- Font: system font (same as app body text), semi-bold (600 weight).
- Dark mode: white text, purple dot (`#a78bfa`).
- Light mode: near-black text (`#0f172a`), purple dot (`#7c3aed`).

### Logo Mark
- Stacked blocks: rounded rectangles of varying widths arranged in rows, representing tasks sorted into time slots.
- Each block uses one of the four quadrant gradient colors.
- Rendered as a single SVG `<symbol>` so all sizes are pixel-identical.
- Layout (within a 72x72 viewBox):
  - Row 1: 34w pink block + 18w purple block
  - Row 2: 22w amber block + 30w purple block (0.7 opacity)
  - Row 3: 40w emerald block + 12w amber block (0.5 opacity)
  - All blocks: 14 height, rx=5 border radius, 4px vertical gap

### App Icon
- Logo mark centered on a deep purple gradient background (`#1e1535` → `#2d1b4e`).
- Adaptive icon with rounded-square mask (22px radius at 96px).
- Generate at standard Android densities: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi.

## 2. Theme & Color System

### Mode Support
- **Light**, **Dark**, and **Auto** (follows system setting).
- User selects mode in Settings screen.
- Disable Material You dynamic color extraction — use fixed Sortd palette for brand consistency.

### Quadrant Gradient Palette
Used for priority indicators, section headers, badges, and the logo mark. Same gradients in both light and dark mode.

| Quadrant | Label | Start | End |
|----------|-------|-------|-----|
| Urgent & Important | Now | `#7c3aed` | `#a78bfa` |
| Important | Next | `#ec4899` | `#f472b6` |
| Urgent | Soon | `#f59e0b` | `#fbbf24` |
| Neither | Later | `#10b981` | `#34d399` |

### Quadrant Display Names
- **Now** (Urgent & Important) — icon: ⚡
- **Next** (Important) — icon: 🎯
- **Soon** (Urgent) — icon: 🔄
- **Later** (Neither) — icon: 📦

The subtitle text (e.g., "Urgent & Important") is shown below the display name in the quadrant selector for clarity.

### Dark Mode Surfaces

| Role | Color |
|------|-------|
| Background | `#0a0a0a` |
| Surface (screen base) | `#1a1625` |
| Card / Input | `#231e30` |
| Elevated | `#2d2640` |
| Border | `#3d3455` |
| Text primary | `#f1f5f9` |
| Text secondary | `#94a3b8` |
| Text tertiary | `#555555` |

### Light Mode Surfaces

| Role | Color |
|------|-------|
| Background | `#fafafa` |
| Surface | `#ffffff` |
| Card / Input | `#f8f7ff` |
| Elevated | `#f1f0ff` |
| Border | `#e9e5ff` |
| Text primary | `#1e293b` |
| Text secondary | `#64748b` |
| Text tertiary | `#94a3b8` |

### Accent Color
- Primary accent: `#7c3aed` (purple).
- Used for: FAB gradient, primary buttons, active states, focus rings, toggle on-state.

### Typography
- Keep existing system font stack and weight hierarchy.
- Headline Large: 28sp Bold, Headline Medium: 22sp Semibold, Title Medium: 16sp Medium, Body: 16sp/14sp Regular, Label: 14sp Medium.

## 3. Task Creation UX

### Form Structure
Single scrollable screen (no structural change), but with polished controls and smart behavior. Fields in order:

1. **Title** (required, single-line)
2. **Description** (optional, 2-4 lines)
3. **Duration**
4. **Deadline** (moved above Priority so smart suggestion is informed by it)
5. **Priority Quadrant**
6. **Day Preference**
7. **Splittable toggle**
8. **"Sort it" button**

### Duration Picker
- **Default view:** Row of tappable chips: `15m`, `30m`, `1h`, `2h`, `4h`, `Custom`.
- Chips use card surface background (`#231e30` dark / `#f8f7ff` light) with border. Active chip gets gradient purple fill.
- **Custom mode:** Tapping "Custom" expands an iOS-style scroll wheel below the chips with two columns (Hours: 0-8, Minutes: 00/15/30/45) and a summary label (e.g., "1h 45m").
- **Smart duration suggestion:** When the user types a title containing recognized keywords (e.g., "meeting" → 30m, "review" → 1h, "standup" → 15m), the matching chip is pre-highlighted with a sparkle badge and a hint line below: "✨ Suggested 1h based on 'design review'". User can tap any other chip to override.

### Keyword-to-Duration Mapping
Simple static map, not ML-based:

| Keywords | Suggested Duration |
|----------|--------------------|
| meeting, sync, standup, huddle, check-in | 30m |
| review, feedback, critique | 1h |
| planning, brainstorm, strategy | 1h |
| write, draft, document, docs | 2h |
| design, prototype, mockup | 2h |
| build, implement, develop, code | 4h |
| research, investigate, explore | 2h |
| email, reply, respond | 15m |
| call, phone | 30m |

Match is case-insensitive, first keyword match wins, checked on every title change with debounce (300ms).

### Deadline Picker
- **Trigger:** Two side-by-side rounded fields — date (📅) and time (🕐). Tapping either opens its picker inline below.
- **Quick-select chips (date):** `Today`, `Tomorrow`, `This Fri`, `Next Week`, `No deadline`. Shown above the calendar.
- **Calendar:** Monthly grid view. Past dates grayed out and non-tappable. Today marked with a dot below the number. Selected date gets gradient purple fill with shadow. Navigation arrows for month switching.
- **Quick-select chips (time):** `9 AM`, `12 PM`, `3 PM`, `5 PM`, `EOD`. Shown above the time wheel.
- **Time wheel:** iOS-style scroll wheel with Hour, Minute (15-min increments), and AM/PM columns. Summary label below (e.g., "5:00 PM").
- **Confirmed state:** After both date and time are set, show a summary bar: "📅 Mar 20, 2026 at 5:00 PM" with a "Clear" button to remove the deadline.

### Priority Quadrant Selector
- **Layout:** Compact 2x2 grid with gradient-tinted backgrounds and borders per quadrant.
- **Each cell shows:** Icon, display name (Now/Next/Soon/Later), subtitle (Urgent & Important / Important / Urgent / Neither).
- **Smart default:** When a deadline is set, the app suggests a quadrant based on proximity:
  - Due today or overdue → suggest **Now** (Urgent & Important)
  - Due within 3 days → suggest **Soon** (Urgent)
  - Due within 1 week → suggest **Next** (Important)
  - Due beyond 1 week or no deadline → no suggestion
- **Suggestion UI:** A banner above the grid: "✨ Suggested **Now** — deadline is tomorrow". The suggested cell gets a brighter background, glow shadow, and a "Suggested" badge. User taps any cell to override.
- The suggestion is a visual hint only — it pre-selects the quadrant but the user can change it freely.

### Other Controls
- **Day Preference:** Row of 7 circular chips (S M T W T F S). Active chips get purple tint. Same as current, restyled.
- **Splittable toggle:** Row with label + subtitle ("Allow splitting across time blocks") and a toggle switch. On-state uses gradient purple track.
- **CTA button:** Full-width, gradient purple (`#7c3aed` → `#a78bfa`), white text, 14px radius, drop shadow. Text: "Sort it ⚡" for create, "Update" for edit.

### Form Styling
- All inputs: card surface background, 12px border radius, 1px border (border color), purple focus ring (2px box-shadow on focus).
- Field labels: 11px uppercase, secondary text color, 0.5px letter spacing.
- Consistent 16px vertical spacing between fields.

## 4. Task List Screen

### Top Bar
- Left: Sortd logo mark (28px SVG) + `sortd.` wordmark.
- Right: Calendar button (📅, purple-tinted surface) + Settings button (⚙️, neutral surface).

### Section Headers
- Gradient dot (8px) + icon + display name (Now/Next/Soon/Later) in quadrant color + task count badge on the right.
- Font: 12px bold uppercase, 1px letter spacing.

### Task Cards
- Background: card surface color.
- Light mode: 1px border in quadrant-tinted color (e.g., `#e9e5ff` for purple, `#fce7f3` for pink).
- Left: 10px gradient dot for quadrant color.
- Center: task title (14px medium) + meta row with duration badge (quadrant-tinted background) and optional deadline text.
- Right: circle check button with quadrant-colored border.
- Card border radius: 14px, padding: 14px.

### Completed Tasks
- Separated by a divider line with centered "Completed · N" label.
- Cards at 45% opacity.
- Title with strikethrough.
- Check button shows ✓ instead of ○.

### FAB
- Gradient purple, 52px square with 16px border radius.
- White "+" icon, centered.
- Drop shadow: `0 4px 20px rgba(124,58,237,0.4)`.
- Positioned bottom-right with 20px margin.

### Scroll Fade
- Gradient fade at bottom edge matching the screen background, ensuring the FAB area feels clean.

## 5. Settings Screen Updates

### Theme Picker
Add a theme selection control to the Settings screen:
- Three options: Light, Dark, Auto.
- Use segmented control or radio group styled with the new palette.
- Persist selection to SharedPreferences.
- "Auto" follows the system dark mode setting.

## 6. Migration Notes

### What Changes
- App name in `strings.xml` and `AndroidManifest.xml`: "Task Tracker" → "Sortd"
- Theme colors in `Color.kt`: replace Eisenhower flat colors with new gradient pairs
- `Theme.kt`: remove dynamic color, add light/dark scheme with Sortd palette, add Auto mode support
- `TaskEditScreen.kt`: new duration picker (chips + wheel), reordered fields (deadline before priority), smart duration suggestion, smart quadrant suggestion, deadline quick-select chips + calendar + time wheel, new CTA text
- `TaskCard.kt`: restyle with new surfaces, gradient dots, quadrant-colored badges
- `TaskListScreen.kt`: new top bar with logo, new section headers with counts, new completed section divider, FAB restyling
- `QuadrantSelector.kt`: new 2x2 grid with gradients, icons, subtitles, smart suggestion badge
- `DurationPicker.kt`: replace +/- stepper composable with chip row + scroll wheel (see detailed note in migration section below)
- App icon resources: new adaptive icon with stacked blocks logo
- Quadrant display names throughout: current labels ("Urgent & Important", "Important", "Urgent", "Neither") are kept as subtitles, new primary display names added (Now, Next, Soon, Later)
- `DurationPicker.kt`: replace existing +/- stepper with new chip row + scroll wheel
- `Theme.kt`: update `TaskTrackerTheme` composable signature and call site in `MainActivity.kt` to remove `dynamicColor` parameter

### What Doesn't Change
- Domain layer (models, scheduling engine, validation)
- Data layer (Room entities, DAOs, repositories, Google Calendar client)
- Navigation structure
- ViewModel logic (except minor additions for smart suggestions)
- WorkManager background sync
- Typography scale
