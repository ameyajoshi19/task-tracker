# Sortd Rebrand & UX Redesign — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand the app from "Task Tracker" to "Sortd" with a new visual identity, custom light/dark/auto theme, and polished task creation UX with smart suggestions.

**Architecture:** UI-layer changes only. Replace Color.kt and Theme.kt with custom Sortd color scheme. Rewrite DurationPicker, QuadrantSelector, DeadlinePicker, DayPreferenceSelector, TaskCard, TaskListScreen, and TaskEditScreen composables. Add theme preference to AppPreferences and Settings. Add smart duration suggestion logic to TaskEditViewModel.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, DataStore Preferences

**Spec:** `docs/superpowers/specs/2026-03-18-sortd-rebrand-and-ux-redesign.md`

---

### Task 1: Color System and Theme

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/theme/Color.kt` (full rewrite, 16 lines)
- Modify: `app/src/main/java/com/tasktracker/ui/theme/Theme.kt` (full rewrite, 31 lines)
- Modify: `app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt:21-27` (add theme key)
- Modify: `app/src/main/java/com/tasktracker/MainActivity.kt:62` (pass theme mode)
- Modify: `app/src/main/res/values/strings.xml:3` (app name)
- Test: `app/src/test/java/com/tasktracker/ui/theme/SortdColorsTest.kt`

- [ ] **Step 1: Write test for color constants**

```kotlin
// app/src/test/java/com/tasktracker/ui/theme/SortdColorsTest.kt
package com.tasktracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SortdColorsTest {
    @Test
    fun `quadrant gradient start colors are correct`() {
        assertEquals(Color(0xFF7C3AED), SortdColors.nowStart)
        assertEquals(Color(0xFFEC4899), SortdColors.nextStart)
        assertEquals(Color(0xFFF59E0B), SortdColors.soonStart)
        assertEquals(Color(0xFF10B981), SortdColors.laterStart)
    }

    @Test
    fun `quadrant gradient end colors are correct`() {
        assertEquals(Color(0xFFA78BFA), SortdColors.nowEnd)
        assertEquals(Color(0xFFF472B6), SortdColors.nextEnd)
        assertEquals(Color(0xFFFBBF24), SortdColors.soonEnd)
        assertEquals(Color(0xFF34D399), SortdColors.laterEnd)
    }

    @Test
    fun `dark surface colors are correct`() {
        assertEquals(Color(0xFF0A0A0A), SortdColors.Dark.background)
        assertEquals(Color(0xFF1A1625), SortdColors.Dark.surface)
        assertEquals(Color(0xFF231E30), SortdColors.Dark.card)
        assertEquals(Color(0xFF2D2640), SortdColors.Dark.elevated)
        assertEquals(Color(0xFF3D3455), SortdColors.Dark.border)
    }

    @Test
    fun `light surface colors are correct`() {
        assertEquals(Color(0xFFFAFAFA), SortdColors.Light.background)
        assertEquals(Color(0xFFFFFFFF), SortdColors.Light.surface)
        assertEquals(Color(0xFFF8F7FF), SortdColors.Light.card)
        assertEquals(Color(0xFFF1F0FF), SortdColors.Light.elevated)
        assertEquals(Color(0xFFE9E5FF), SortdColors.Light.border)
    }

    @Test
    fun `accent color is purple`() {
        assertEquals(Color(0xFF7C3AED), SortdColors.accent)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.tasktracker.ui.theme.SortdColorsTest" --info`
Expected: FAIL — `SortdColors` does not exist

- [ ] **Step 3: Rewrite Color.kt with Sortd palette**

```kotlin
// app/src/main/java/com/tasktracker/ui/theme/Color.kt
package com.tasktracker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object SortdColors {
    // Accent
    val accent = Color(0xFF7C3AED)
    val accentLight = Color(0xFFA78BFA)

    // Quadrant gradients — start/end pairs
    val nowStart = Color(0xFF7C3AED)
    val nowEnd = Color(0xFFA78BFA)
    val nextStart = Color(0xFFEC4899)
    val nextEnd = Color(0xFFF472B6)
    val soonStart = Color(0xFFF59E0B)
    val soonEnd = Color(0xFFFBBF24)
    val laterStart = Color(0xFF10B981)
    val laterEnd = Color(0xFF34D399)

    // Quadrant gradient brushes
    val nowGradient = Brush.linearGradient(listOf(nowStart, nowEnd))
    val nextGradient = Brush.linearGradient(listOf(nextStart, nextEnd))
    val soonGradient = Brush.linearGradient(listOf(soonStart, soonEnd))
    val laterGradient = Brush.linearGradient(listOf(laterStart, laterEnd))

    object Dark {
        val background = Color(0xFF0A0A0A)
        val surface = Color(0xFF1A1625)
        val card = Color(0xFF231E30)
        val elevated = Color(0xFF2D2640)
        val border = Color(0xFF3D3455)
        val textPrimary = Color(0xFFF1F5F9)
        val textSecondary = Color(0xFF94A3B8)
        val textTertiary = Color(0xFF555555)
    }

    object Light {
        val background = Color(0xFFFAFAFA)
        val surface = Color(0xFFFFFFFF)
        val card = Color(0xFFF8F7FF)
        val elevated = Color(0xFFF1F0FF)
        val border = Color(0xFFE9E5FF)
        val textPrimary = Color(0xFF1E293B)
        val textSecondary = Color(0xFF64748B)
        val textTertiary = Color(0xFF94A3B8)
    }
}

// Legacy aliases for compatibility during migration (remove after all files are updated)
val QuadrantUrgentImportant = SortdColors.nowStart
val QuadrantImportant = SortdColors.nextStart
val QuadrantUrgent = SortdColors.soonStart
val QuadrantNeither = SortdColors.laterStart
val StatusPending = Color(0xFF9E9E9E)
val StatusScheduled = SortdColors.nowStart
val StatusInProgress = SortdColors.soonStart
val StatusCompleted = SortdColors.laterStart
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.tasktracker.ui.theme.SortdColorsTest" --info`
Expected: PASS

- [ ] **Step 5: Add theme preference to AppPreferences**

Add to `app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt`:

In the companion object (after line 26), add:
```kotlin
private val THEME_MODE = stringPreferencesKey("theme_mode")
```

After `setOnboardingCompleted` (after line 66), add:
```kotlin
val themeMode: Flow<String> = context.dataStore.data
    .map { it[THEME_MODE] ?: "auto" }

suspend fun setThemeMode(mode: String) {
    context.dataStore.edit { it[THEME_MODE] = mode }
}
```

- [ ] **Step 6: Rewrite Theme.kt with Sortd color scheme**

```kotlin
// app/src/main/java/com/tasktracker/ui/theme/Theme.kt
package com.tasktracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SortdDarkColorScheme = darkColorScheme(
    primary = SortdColors.accent,
    onPrimary = Color.White,
    primaryContainer = SortdColors.Dark.elevated,
    onPrimaryContainer = SortdColors.accentLight,
    secondary = SortdColors.accentLight,
    background = SortdColors.Dark.background,
    onBackground = SortdColors.Dark.textPrimary,
    surface = SortdColors.Dark.surface,
    onSurface = SortdColors.Dark.textPrimary,
    surfaceVariant = SortdColors.Dark.card,
    onSurfaceVariant = SortdColors.Dark.textSecondary,
    outline = SortdColors.Dark.border,
    outlineVariant = SortdColors.Dark.border,
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF3B1111),
    onErrorContainer = Color(0xFFFCA5A5),
)

private val SortdLightColorScheme = lightColorScheme(
    primary = SortdColors.accent,
    onPrimary = Color.White,
    primaryContainer = SortdColors.Light.elevated,
    onPrimaryContainer = SortdColors.accent,
    secondary = SortdColors.accentLight,
    background = SortdColors.Light.background,
    onBackground = SortdColors.Light.textPrimary,
    surface = SortdColors.Light.surface,
    onSurface = SortdColors.Light.textPrimary,
    surfaceVariant = SortdColors.Light.card,
    onSurfaceVariant = SortdColors.Light.textSecondary,
    outline = SortdColors.Light.border,
    outlineVariant = SortdColors.Light.border,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

@Composable
fun SortdTheme(
    themeMode: String = "auto",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) SortdDarkColorScheme else SortdLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TaskTrackerTypography,
        content = content,
    )
}

// Keep old name as alias during migration
@Composable
fun TaskTrackerTheme(
    themeMode: String = "auto",
    content: @Composable () -> Unit,
) = SortdTheme(themeMode = themeMode, content = content)
```

- [ ] **Step 7: Update MainActivity to pass theme mode**

Replace lines 61-62 of `app/src/main/java/com/tasktracker/MainActivity.kt`:
```kotlin
// Old:
TaskTrackerTheme {

// New:
val themeMode by appPreferences.themeMode
    .collectAsState(initial = "auto")
TaskTrackerTheme(themeMode = themeMode) {
```

- [ ] **Step 8: Update app name in strings.xml**

Change line 3 of `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">Sortd</string>
```

- [ ] **Step 9: Run all tests and verify build**

Run: `./gradlew test --info`
Expected: All tests PASS (no existing tests should break since we kept legacy aliases)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/theme/Color.kt \
       app/src/main/java/com/tasktracker/ui/theme/Theme.kt \
       app/src/main/java/com/tasktracker/data/preferences/AppPreferences.kt \
       app/src/main/java/com/tasktracker/MainActivity.kt \
       app/src/main/res/values/strings.xml \
       app/src/test/java/com/tasktracker/ui/theme/SortdColorsTest.kt
git commit -m "feat: add Sortd color system, theme, and rename app"
```

---

### Task 2: Duration Picker with Chips and Scroll Wheel

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/DurationPicker.kt` (full rewrite, 69 lines)
- Test: `app/src/test/java/com/tasktracker/ui/components/DurationSuggestionTest.kt`

- [ ] **Step 1: Write test for duration suggestion logic**

```kotlin
// app/src/test/java/com/tasktracker/ui/components/DurationSuggestionTest.kt
package com.tasktracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationSuggestionTest {
    @Test
    fun `meeting suggests 30 minutes`() {
        assertEquals(30, suggestDuration("Team meeting"))
    }

    @Test
    fun `standup suggests 30 minutes`() {
        assertEquals(30, suggestDuration("Daily standup"))
    }

    @Test
    fun `review suggests 60 minutes`() {
        assertEquals(60, suggestDuration("Design review with team"))
    }

    @Test
    fun `email suggests 15 minutes`() {
        assertEquals(15, suggestDuration("Reply to vendor email"))
    }

    @Test
    fun `build suggests 240 minutes`() {
        assertEquals(240, suggestDuration("Build payment integration"))
    }

    @Test
    fun `write suggests 120 minutes`() {
        assertEquals(120, suggestDuration("Write API documentation"))
    }

    @Test
    fun `research suggests 120 minutes`() {
        assertEquals(120, suggestDuration("Research caching strategies"))
    }

    @Test
    fun `no keyword returns null`() {
        assertNull(suggestDuration("Fix bug"))
    }

    @Test
    fun `case insensitive matching`() {
        assertEquals(30, suggestDuration("TEAM MEETING"))
    }

    @Test
    fun `first match wins`() {
        // "review meeting" — "review" comes first alphabetically but
        // we check title left-to-right, so whichever keyword appears first wins
        assertEquals(60, suggestDuration("review meeting"))
    }

    @Test
    fun `empty title returns null`() {
        assertNull(suggestDuration(""))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.tasktracker.ui.components.DurationSuggestionTest" --info`
Expected: FAIL — `suggestDuration` does not exist

- [ ] **Step 3: Rewrite DurationPicker.kt with chips, wheel, and suggestion logic**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/DurationPicker.kt
package com.tasktracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import kotlinx.coroutines.launch

private val DURATION_KEYWORDS: List<Pair<List<String>, Int>> = listOf(
    listOf("meeting", "sync", "standup", "huddle", "check-in") to 30,
    listOf("review", "feedback", "critique") to 60,
    listOf("planning", "brainstorm", "strategy") to 60,
    listOf("write", "draft", "document", "docs") to 120,
    listOf("design", "prototype", "mockup") to 120,
    listOf("build", "implement", "develop", "code") to 240,
    listOf("research", "investigate", "explore") to 120,
    listOf("email", "reply", "respond") to 15,
    listOf("call", "phone") to 30,
)

fun suggestDuration(title: String): Int? {
    if (title.isBlank()) return null
    val lower = title.lowercase()
    // Find the keyword that appears earliest in the title
    var bestIndex = Int.MAX_VALUE
    var bestDuration: Int? = null
    for ((keywords, duration) in DURATION_KEYWORDS) {
        for (keyword in keywords) {
            val idx = lower.indexOf(keyword)
            if (idx != -1 && idx < bestIndex) {
                bestIndex = idx
                bestDuration = duration
            }
        }
    }
    return bestDuration
}

private data class DurationChip(val label: String, val minutes: Int)

private val PRESET_CHIPS = listOf(
    DurationChip("15m", 15),
    DurationChip("30m", 30),
    DurationChip("1h", 60),
    DurationChip("2h", 120),
    DurationChip("4h", 240),
)

@Composable
fun DurationPicker(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    suggestedMinutes: Int? = null,
    suggestedKeyword: String? = null,
) {
    var showCustom by remember { mutableStateOf(false) }
    val isCustom = PRESET_CHIPS.none { it.minutes == durationMinutes } || showCustom
    val isDark = MaterialTheme.colorScheme.background == SortdColors.Dark.background

    Column(modifier = modifier) {
        Text(
            text = "DURATION",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_CHIPS.forEach { chip ->
                val isActive = chip.minutes == durationMinutes && !showCustom
                val isSuggested = chip.minutes == suggestedMinutes && !isActive
                val chipShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .clip(chipShape)
                        .then(
                            if (isActive) Modifier.background(
                                Brush.linearGradient(
                                    listOf(SortdColors.accent, SortdColors.accentLight)
                                )
                            )
                            else Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, chipShape)
                        )
                        .clickable {
                            showCustom = false
                            onDurationChange(chip.minutes)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chip.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) Color.White
                        else if (isSuggested) SortdColors.accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isActive || isSuggested) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
            // Custom chip
            val customShape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .clip(customShape)
                    .then(
                        if (isCustom && showCustom) Modifier.background(
                            Brush.linearGradient(
                                listOf(SortdColors.accent, SortdColors.accentLight)
                            )
                        )
                        else Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, customShape)
                    )
                    .clickable { showCustom = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCustom && showCustom) Color.White else SortdColors.accent,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Suggestion hint
        if (suggestedMinutes != null && suggestedKeyword != null) {
            Text(
                text = "✨ Suggested ${formatDuration(suggestedMinutes)} based on \"$suggestedKeyword\"",
                style = MaterialTheme.typography.bodySmall,
                color = SortdColors.accent,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // Custom scroll wheel
        AnimatedVisibility(visible = showCustom) {
            CustomDurationWheel(
                durationMinutes = durationMinutes,
                onDurationChange = onDurationChange,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun CustomDurationWheel(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    val hourOptions = (0..8).toList()
    val minuteOptions = listOf(0, 15, 30, 45)
    val scope = rememberCoroutineScope()

    val hourListState = rememberLazyListState(initialFirstVisibleItemIndex = hours.coerceIn(0, 8))
    val minuteListState = rememberLazyListState(
        initialFirstVisibleItemIndex = minuteOptions.indexOf(minutes).coerceAtLeast(0)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Hours column
            WheelColumn(
                items = hourOptions.map { it.toString() },
                selectedIndex = hours.coerceIn(0, 8),
                onSelectedChange = { idx ->
                    val newMinutes = (hourOptions[idx] * 60 + minutes).coerceIn(15, 480)
                    onDurationChange(newMinutes)
                },
                listState = hourListState,
                label = "HOURS",
            )
            Text(
                ":",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            // Minutes column
            WheelColumn(
                items = minuteOptions.map { it.toString().padStart(2, '0') },
                selectedIndex = minuteOptions.indexOf(minutes).coerceAtLeast(0),
                onSelectedChange = { idx ->
                    val newMinutes = (hours * 60 + minuteOptions[idx]).coerceIn(15, 480)
                    onDurationChange(newMinutes)
                },
                listState = minuteListState,
                label = "MIN",
            )
        }
        Text(
            text = formatDuration(durationMinutes),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = SortdColors.accentLight,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.height(100.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(items.size) { index ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SortdColors.accent.copy(alpha = 0.1f))
                                else Modifier
                            )
                            .clickable { onSelectedChange(index) }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = items[index],
                            fontSize = if (isSelected) 26.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Light,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.tasktracker.ui.components.DurationSuggestionTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/DurationPicker.kt \
       app/src/test/java/com/tasktracker/ui/components/DurationSuggestionTest.kt
git commit -m "feat: replace duration picker with chips + scroll wheel + smart suggestions"
```

---

### Task 3: Quadrant Selector with Smart Suggestions

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/QuadrantSelector.kt` (full rewrite, 97 lines)
- Test: `app/src/test/java/com/tasktracker/ui/components/QuadrantSuggestionTest.kt`

- [ ] **Step 1: Write test for quadrant suggestion logic**

```kotlin
// app/src/test/java/com/tasktracker/ui/components/QuadrantSuggestionTest.kt
package com.tasktracker.ui.components

import com.tasktracker.domain.model.Quadrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class QuadrantSuggestionTest {
    private val now = Instant.now()

    @Test
    fun `no deadline returns null`() {
        assertNull(suggestQuadrant(null, now))
    }

    @Test
    fun `deadline today suggests Now`() {
        val deadline = now.plus(6, ChronoUnit.HOURS)
        assertEquals(Quadrant.URGENT_IMPORTANT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `overdue deadline suggests Now`() {
        val deadline = now.minus(1, ChronoUnit.HOURS)
        assertEquals(Quadrant.URGENT_IMPORTANT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `deadline in 2 days suggests Soon`() {
        val deadline = now.plus(2, ChronoUnit.DAYS)
        assertEquals(Quadrant.URGENT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `deadline in 5 days suggests Next`() {
        val deadline = now.plus(5, ChronoUnit.DAYS)
        assertEquals(Quadrant.IMPORTANT, suggestQuadrant(deadline, now))
    }

    @Test
    fun `deadline beyond 1 week returns null`() {
        val deadline = now.plus(10, ChronoUnit.DAYS)
        assertNull(suggestQuadrant(deadline, now))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.tasktracker.ui.components.QuadrantSuggestionTest" --info`
Expected: FAIL — `suggestQuadrant` does not exist

- [ ] **Step 3: Rewrite QuadrantSelector.kt**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/QuadrantSelector.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.ui.theme.SortdColors
import java.time.Instant
import java.time.temporal.ChronoUnit

fun suggestQuadrant(deadline: Instant?, now: Instant): Quadrant? {
    if (deadline == null) return null
    val hoursUntil = ChronoUnit.HOURS.between(now, deadline)
    return when {
        hoursUntil <= 24 -> Quadrant.URGENT_IMPORTANT  // Due today or overdue
        hoursUntil <= 72 -> Quadrant.URGENT              // Within 3 days
        hoursUntil <= 168 -> Quadrant.IMPORTANT           // Within 1 week
        else -> null                                       // Beyond 1 week
    }
}

private data class QuadrantInfo(
    val quadrant: Quadrant,
    val icon: String,
    val displayName: String,
    val subtitle: String,
    val colorStart: Color,
    val colorEnd: Color,
)

private val QUADRANT_INFO = listOf(
    QuadrantInfo(Quadrant.URGENT_IMPORTANT, "⚡", "Now", "Urgent & Important", SortdColors.nowStart, SortdColors.nowEnd),
    QuadrantInfo(Quadrant.IMPORTANT, "🎯", "Next", "Important", SortdColors.nextStart, SortdColors.nextEnd),
    QuadrantInfo(Quadrant.URGENT, "🔄", "Soon", "Urgent", SortdColors.soonStart, SortdColors.soonEnd),
    QuadrantInfo(Quadrant.NEITHER, "📦", "Later", "Neither", SortdColors.laterStart, SortdColors.laterEnd),
)

@Composable
fun QuadrantSelector(
    selected: Quadrant,
    onSelect: (Quadrant) -> Unit,
    modifier: Modifier = Modifier,
    suggestedQuadrant: Quadrant? = null,
    suggestionReason: String? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = "PRIORITY",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Smart suggestion banner
        if (suggestedQuadrant != null && suggestionReason != null) {
            val info = QUADRANT_INFO.first { it.quadrant == suggestedQuadrant }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(info.colorStart.copy(alpha = 0.1f))
                    .border(1.dp, info.colorStart.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(8.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✨", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Suggested ",
                    style = MaterialTheme.typography.bodySmall,
                    color = info.colorStart,
                )
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = info.colorEnd,
                )
                Text(
                    text = " — $suggestionReason",
                    style = MaterialTheme.typography.bodySmall,
                    color = info.colorStart,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // 2x2 grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadrantCell(QUADRANT_INFO[0], selected, suggestedQuadrant, onSelect, Modifier.weight(1f))
                QuadrantCell(QUADRANT_INFO[1], selected, suggestedQuadrant, onSelect, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuadrantCell(QUADRANT_INFO[2], selected, suggestedQuadrant, onSelect, Modifier.weight(1f))
                QuadrantCell(QUADRANT_INFO[3], selected, suggestedQuadrant, onSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    info: QuadrantInfo,
    selected: Quadrant,
    suggested: Quadrant?,
    onSelect: (Quadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = info.quadrant == selected
    val isSuggested = info.quadrant == suggested
    val shape = RoundedCornerShape(12.dp)
    val bgAlpha = when {
        isSelected || isSuggested -> 0.25f
        else -> 0.12f
    }
    val borderColor = when {
        isSelected || isSuggested -> info.colorStart
        else -> info.colorStart.copy(alpha = 0.25f)
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(info.colorStart.copy(alpha = bgAlpha))
            .border(1.dp, borderColor, shape)
            .clickable { onSelect(info.quadrant) }
            .padding(10.dp, 10.dp),
    ) {
        Text(info.icon, fontSize = 16.sp)
        Text(
            text = info.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = info.colorStart,
        )
        Text(
            text = info.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = info.colorStart.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
        if (isSuggested && !isSelected) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "SUGGESTED",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(info.colorStart)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.tasktracker.ui.components.QuadrantSuggestionTest" --info`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/QuadrantSelector.kt \
       app/src/test/java/com/tasktracker/ui/components/QuadrantSuggestionTest.kt
git commit -m "feat: quadrant selector with gradient cells, labels, and smart suggestion"
```

---

### Task 4: Deadline Picker with Calendar, Time Wheel, and Quick Chips

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/components/DeadlinePicker.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt:178-263` (remove old DeadlinePicker)

- [ ] **Step 1: Create DeadlinePicker component**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/DeadlinePicker.kt
package com.tasktracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun DeadlinePicker(
    deadline: Instant?,
    onDeadlineChange: (Instant?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember(deadline) {
        mutableStateOf(deadline?.let { LocalDateTime.ofInstant(it, zoneId).toLocalDate() })
    }
    var selectedTime by remember(deadline) {
        mutableStateOf(deadline?.let { LocalDateTime.ofInstant(it, zoneId).toLocalTime() })
    }
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }

    Column(modifier = modifier) {
        Text(
            text = "DEADLINE",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        // Date and time trigger fields
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerField(
                icon = "📅",
                text = selectedDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                placeholder = "Set date",
                isActive = showDatePicker,
                onClick = {
                    showDatePicker = !showDatePicker
                    showTimePicker = false
                },
                modifier = Modifier.weight(1f),
            )
            TriggerField(
                icon = "🕐",
                text = selectedTime?.format(DateTimeFormatter.ofPattern("h:mm a")),
                placeholder = "Set time",
                isActive = showTimePicker,
                onClick = {
                    showTimePicker = !showTimePicker
                    showDatePicker = false
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Date picker section
        AnimatedVisibility(visible = showDatePicker) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Quick date chips
                val today = LocalDate.now(zoneId)
                val quickDates = listOf(
                    "Today" to today,
                    "Tomorrow" to today.plusDays(1),
                    "This Fri" to today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)),
                    "Next Week" to today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickDates.forEach { (label, date) ->
                        QuickChip(
                            label = label,
                            isActive = selectedDate == date,
                            onClick = {
                                selectedDate = date
                                updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                                showDatePicker = false
                                if (selectedTime == null) showTimePicker = true
                            },
                        )
                    }
                    QuickChip(
                        label = "No deadline",
                        isActive = false,
                        onClick = {
                            selectedDate = null
                            selectedTime = null
                            onDeadlineChange(null)
                            showDatePicker = false
                        },
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Calendar grid
                CalendarGrid(
                    displayMonth = displayMonth,
                    selectedDate = selectedDate,
                    today = today,
                    onDateSelected = { date ->
                        selectedDate = date
                        updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                        showDatePicker = false
                        if (selectedTime == null) showTimePicker = true
                    },
                    onMonthChange = { displayMonth = it },
                )
            }
        }

        // Time picker section
        AnimatedVisibility(visible = showTimePicker) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Quick time chips
                val quickTimes = listOf(
                    "9 AM" to LocalTime.of(9, 0),
                    "12 PM" to LocalTime.of(12, 0),
                    "3 PM" to LocalTime.of(15, 0),
                    "5 PM" to LocalTime.of(17, 0),
                    "EOD" to LocalTime.of(23, 59),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickTimes.forEach { (label, time) ->
                        QuickChip(
                            label = label,
                            isActive = selectedTime == time,
                            onClick = {
                                selectedTime = time
                                updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                                showTimePicker = false
                            },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Time wheel
                TimeWheel(
                    selectedTime = selectedTime ?: LocalTime.of(17, 0),
                    onTimeSelected = { time ->
                        selectedTime = time
                        updateDeadline(selectedDate, selectedTime, zoneId, onDeadlineChange)
                    },
                )
            }
        }

        // Confirmed deadline summary
        if (selectedDate != null && selectedTime != null && !showDatePicker && !showTimePicker) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SortdColors.accent.copy(alpha = 0.1f))
                    .border(1.dp, SortdColors.accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(10.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📅 ${selectedDate!!.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} at ${selectedTime!!.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = SortdColors.accentLight,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .clickable {
                            selectedDate = null
                            selectedTime = null
                            onDeadlineChange(null)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TriggerField(
    icon: String,
    text: String?,
    placeholder: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isActive) Modifier.border(
                    1.dp, SortdColors.accent, RoundedCornerShape(12.dp)
                )
                else Modifier.border(
                    1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)
                )
            )
            .clickable(onClick = onClick)
            .padding(10.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text ?: placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = if (text != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (isActive) SortdColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .then(
                if (isActive) Modifier
                    .background(SortdColors.accent.copy(alpha = 0.2f))
                    .border(1.dp, SortdColors.accent, shape)
                else Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontSize = 11.sp,
    )
}

@Composable
private fun CalendarGrid(
    displayMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        // Month header with nav arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row {
                Text(
                    "‹",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .clickable { onMonthChange(displayMonth.minusMonths(1)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 18.sp,
                    color = SortdColors.accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "›",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .clickable { onMonthChange(displayMonth.plusMonths(1)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 18.sp,
                    color = SortdColors.accent,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Weekday headers
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Calendar days
        val firstDay = displayMonth.atDay(1)
        val startPadding = firstDay.dayOfWeek.value % 7 // Sunday = 0
        val daysInMonth = displayMonth.lengthOfMonth()

        val totalCells = startPadding + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startPadding + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = displayMonth.atDay(dayNum)
                        val isPast = date.isBefore(today)
                        val isToday = date == today
                        val isSelected = date == selectedDate

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    when {
                                        isSelected -> Modifier.background(
                                            Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight))
                                        )
                                        else -> Modifier
                                    }
                                )
                                .then(
                                    if (!isPast) Modifier.clickable { onDateSelected(date) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNum.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> Color.White
                                        isPast -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        isToday -> SortdColors.accent
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                if (isToday && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(SortdColors.accent),
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeWheel(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
) {
    var hour by remember(selectedTime) { mutableIntStateOf(if (selectedTime.hour % 12 == 0) 12 else selectedTime.hour % 12) }
    var minute by remember(selectedTime) { mutableIntStateOf(selectedTime.minute / 15 * 15) }
    var isAm by remember(selectedTime) { mutableStateOf(selectedTime.hour < 12) }

    val hourOptions = (1..12).toList()
    val minuteOptions = listOf(0, 15, 30, 45)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Hour
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                listOf(hour - 1, hour, hour + 1).forEach { h ->
                    val display = when {
                        h < 1 -> h + 12
                        h > 12 -> h - 12
                        else -> h
                    }
                    val isSel = h == hour
                    Text(
                        text = display.toString(),
                        fontSize = if (isSel) 26.sp else 18.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Light,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSel) Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SortdColors.accent.copy(alpha = 0.1f))
                                else Modifier
                            )
                            .clickable {
                                hour = display
                                emitTime(hour, minute, isAm, onTimeSelected)
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Text("HOUR", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            Text(":", fontSize = 22.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))

            // Minute
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                val curIdx = minuteOptions.indexOf(minute).coerceAtLeast(0)
                listOf(curIdx - 1, curIdx, curIdx + 1).forEach { idx ->
                    val wrappedIdx = (idx + minuteOptions.size) % minuteOptions.size
                    val isSel = idx == curIdx
                    Text(
                        text = minuteOptions[wrappedIdx].toString().padStart(2, '0'),
                        fontSize = if (isSel) 26.sp else 18.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Light,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSel) Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SortdColors.accent.copy(alpha = 0.1f))
                                else Modifier
                            )
                            .clickable {
                                minute = minuteOptions[wrappedIdx]
                                emitTime(hour, minute, isAm, onTimeSelected)
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Text("MIN", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            Spacer(Modifier.width(8.dp))

            // AM/PM
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val amShape = RoundedCornerShape(8.dp)
                Text(
                    text = "AM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isAm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(amShape)
                        .then(
                            if (isAm) Modifier.background(Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight)))
                            else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        )
                        .clickable {
                            isAm = true
                            emitTime(hour, minute, isAm, onTimeSelected)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    text = "PM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!isAm) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(amShape)
                        .then(
                            if (!isAm) Modifier.background(Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight)))
                            else Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        )
                        .clickable {
                            isAm = false
                            emitTime(hour, minute, isAm, onTimeSelected)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // Summary
        val displayTime = LocalTime.of(
            if (isAm) (if (hour == 12) 0 else hour) else (if (hour == 12) 12 else hour + 12),
            minute,
        )
        Text(
            text = displayTime.format(DateTimeFormatter.ofPattern("h:mm a")),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = SortdColors.accentLight,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun emitTime(hour: Int, minute: Int, isAm: Boolean, onTimeSelected: (LocalTime) -> Unit) {
    val h24 = if (isAm) (if (hour == 12) 0 else hour) else (if (hour == 12) 12 else hour + 12)
    onTimeSelected(LocalTime.of(h24, minute))
}

private fun updateDeadline(
    date: LocalDate?,
    time: LocalTime?,
    zoneId: ZoneId,
    onDeadlineChange: (Instant?) -> Unit,
) {
    if (date != null && time != null) {
        onDeadlineChange(date.atTime(time).atZone(zoneId).toInstant())
    } else if (date != null) {
        // Set tentative deadline at end of day so suggestion logic can work
        onDeadlineChange(date.atTime(23, 59).atZone(zoneId).toInstant())
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug --info 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/DeadlinePicker.kt
git commit -m "feat: add inline deadline picker with calendar, time wheel, and quick chips"
```

---

### Task 5: DayPreferenceSelector Restyle

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/DayPreferenceSelector.kt` (full rewrite, 41 lines)

**Note:** The spec describes "7 circular chips (S M T W T F S)" but the domain model (`DayPreference` enum) only supports `WEEKDAY`, `WEEKEND`, and `ANY`. Implementing per-day selection would require domain model changes outside this spec's scope. We keep the existing 3-chip model, restyled to match Sortd.

- [ ] **Step 1: Rewrite DayPreferenceSelector with Sortd-styled chips**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/DayPreferenceSelector.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.DayPreference
import com.tasktracker.ui.theme.SortdColors

@Composable
fun DayPreferenceSelector(
    selected: DayPreference,
    onSelect: (DayPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "PREFERRED DAYS",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayPreference.entries.forEach { pref ->
                val isActive = selected == pref
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .then(
                            if (isActive) Modifier
                                .background(SortdColors.accent.copy(alpha = 0.2f))
                                .border(1.dp, SortdColors.accent, shape)
                            else Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        )
                        .clickable { onSelect(pref) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (pref) {
                            DayPreference.WEEKDAY -> "Weekday"
                            DayPreference.WEEKEND -> "Weekend"
                            DayPreference.ANY -> "Any"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) SortdColors.accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug --info 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/DayPreferenceSelector.kt
git commit -m "feat: restyle day preference selector with Sortd chip design"
```

---

### Task 6: TaskEditScreen Rewrite

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt` (full rewrite, 264 lines)
- Modify: `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt:21-35,80-86` (add suggestion state)

- [ ] **Step 1: Add suggestion fields to TaskEditViewModel**

In `app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt`, add to `TaskEditUiState` (after line 34):
```kotlin
val suggestedDurationMinutes: Int? = null,
val suggestedDurationKeyword: String? = null,
val suggestedQuadrant: Quadrant? = null,
val suggestedQuadrantReason: String? = null,
```

Update `updateTitle` (line 80) to include duration suggestion:
```kotlin
fun updateTitle(title: String) {
    val suggestion = com.tasktracker.ui.components.suggestDuration(title)
    val keyword = if (suggestion != null) {
        title.lowercase().split(" ").firstOrNull { word ->
            com.tasktracker.ui.components.suggestDuration(word) != null
        }
    } else null
    _uiState.update {
        it.copy(
            title = title,
            validationError = null,
            suggestedDurationMinutes = suggestion,
            suggestedDurationKeyword = keyword,
        )
    }
}
```

Update `updateDeadline` (line 84) to include quadrant suggestion:
```kotlin
fun updateDeadline(deadline: Instant?) {
    val suggested = deadline?.let {
        com.tasktracker.ui.components.suggestQuadrant(it, Instant.now())
    }
    val reason = deadline?.let {
        val hoursUntil = java.time.temporal.ChronoUnit.HOURS.between(Instant.now(), it)
        when {
            hoursUntil <= 24 -> "deadline is today"
            hoursUntil <= 72 -> "deadline in ${hoursUntil / 24 + 1} days"
            hoursUntil <= 168 -> "deadline this week"
            else -> null
        }
    }
    _uiState.update {
        var newState = it.copy(
            deadline = deadline,
            suggestedQuadrant = suggested,
            suggestedQuadrantReason = reason,
        )
        // Auto-select suggested quadrant if user hasn't manually changed it
        if (suggested != null && it.quadrant == it.suggestedQuadrant ?: Quadrant.IMPORTANT) {
            newState = newState.copy(quadrant = suggested)
        }
        newState
    }
}
```

- [ ] **Step 2: Rewrite TaskEditScreen.kt**

```kotlin
// app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt
package com.tasktracker.ui.taskedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.SchedulingResult
import com.tasktracker.ui.components.*
import com.tasktracker.ui.theme.SortdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReschedule: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) onNavigateBack()
    }

    LaunchedEffect(uiState.schedulingResult) {
        if (uiState.schedulingResult is SchedulingResult.NeedsReschedule) {
            onNavigateToReschedule()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Task" else "New Task") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Stale data warning
            if (uiState.staleDataWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "Calendar data may be outdated. Schedule might conflict with recent calendar changes.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Title
            Column {
                Text(
                    text = "TITLE",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.validationError != null && uiState.title.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SortdColors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }

            // Description
            Column {
                Text(
                    text = "DESCRIPTION",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::updateDescription,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SortdColors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }

            // Duration
            DurationPicker(
                durationMinutes = uiState.durationMinutes,
                onDurationChange = viewModel::updateDuration,
                suggestedMinutes = uiState.suggestedDurationMinutes,
                suggestedKeyword = uiState.suggestedDurationKeyword,
            )

            // Deadline (moved above Priority)
            DeadlinePicker(
                deadline = uiState.deadline,
                onDeadlineChange = viewModel::updateDeadline,
            )

            // Priority Quadrant
            QuadrantSelector(
                selected = uiState.quadrant,
                onSelect = viewModel::updateQuadrant,
                suggestedQuadrant = uiState.suggestedQuadrant,
                suggestionReason = uiState.suggestedQuadrantReason,
            )

            // Day Preference
            DayPreferenceSelector(
                selected = uiState.dayPreference,
                onSelect = viewModel::updateDayPreference,
            )

            // Splittable toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp, 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Splittable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Allow splitting across time blocks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Switch(
                    checked = uiState.splittable,
                    onCheckedChange = viewModel::updateSplittable,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = SortdColors.accent,
                        checkedThumbColor = Color.White,
                    ),
                )
            }

            // Validation errors
            if (uiState.validationError != null) {
                Text(
                    text = uiState.validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val result = uiState.schedulingResult
            if (result is SchedulingResult.DeadlineAtRisk) {
                Text(text = result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (result is SchedulingResult.NoSlotsAvailable) {
                Text(text = result.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // CTA Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (!uiState.isSaving) Brush.linearGradient(listOf(SortdColors.accent, SortdColors.accentLight))
                        else Brush.linearGradient(listOf(SortdColors.accent.copy(alpha = 0.5f), SortdColors.accentLight.copy(alpha = 0.5f)))
                    )
                    .then(if (!uiState.isSaving) Modifier.clickable(onClick = viewModel::save) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = if (uiState.isEditing) "Update" else "Sort it ⚡",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
```

- [ ] **Step 3: Run all tests**

Run: `./gradlew test --info`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/taskedit/TaskEditScreen.kt \
       app/src/main/java/com/tasktracker/ui/taskedit/TaskEditViewModel.kt
git commit -m "feat: redesign task edit screen with smart suggestions and Sortd styling"
```

---

### Task 7: TaskCard Restyle

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/TaskCard.kt` (full rewrite, 96 lines)

- [ ] **Step 1: Rewrite TaskCard.kt**

```kotlin
// app/src/main/java/com/tasktracker/ui/components/TaskCard.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.ui.theme.SortdColors

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val (colorStart, colorEnd) = quadrantColors(task.quadrant)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isCompleted) 0.45f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gradient quadrant dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        )
        Spacer(Modifier.width(12.dp))

        // Task info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Duration badge
                Text(
                    text = formatDuration(task.estimatedDurationMinutes),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorStart,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorStart.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        // Check button
        if (!isCompleted) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorStart.copy(alpha = 0.12f))
                    .border(1.5.dp, colorStart.copy(alpha = 0.4f), CircleShape)
                    .clickable(onClick = onComplete),
                contentAlignment = Alignment.Center,
            ) {
                Text("○", fontSize = 14.sp, color = colorStart)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colorStart.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Completed",
                    modifier = Modifier.size(16.dp),
                    tint = colorStart,
                )
            }
        }
    }
}

fun quadrantColors(quadrant: Quadrant): Pair<Color, Color> = when (quadrant) {
    Quadrant.URGENT_IMPORTANT -> SortdColors.nowStart to SortdColors.nowEnd
    Quadrant.IMPORTANT -> SortdColors.nextStart to SortdColors.nextEnd
    Quadrant.URGENT -> SortdColors.soonStart to SortdColors.soonEnd
    Quadrant.NEITHER -> SortdColors.laterStart to SortdColors.laterEnd
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug --info 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/TaskCard.kt
git commit -m "feat: restyle task card with gradient dots, duration badges, and circle check"
```

---

### Task 8: TaskListScreen Redesign

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt` (full rewrite, 125 lines)

- [ ] **Step 1: Rewrite TaskListScreen.kt**

```kotlin
// app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
package com.tasktracker.ui.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.domain.model.Task
import com.tasktracker.domain.model.TaskStatus
import com.tasktracker.ui.components.TaskCard
import com.tasktracker.ui.components.quadrantColors
import com.tasktracker.ui.theme.SortdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onAddTask: () -> Unit,
    onEditTask: (Long) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "sortd",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            ".",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SortdColors.accent,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSchedule) {
                        Icon(Icons.Default.CalendarMonth, "Schedule", tint = SortdColors.accent)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                shape = RoundedCornerShape(16.dp),
                containerColor = SortdColors.accent,
                contentColor = Color.White,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Default.Add, "Add task")
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SortdColors.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val quadrantOrder = listOf(
                    Quadrant.URGENT_IMPORTANT,
                    Quadrant.IMPORTANT,
                    Quadrant.URGENT,
                    Quadrant.NEITHER,
                )
                for (quadrant in quadrantOrder) {
                    val tasks = uiState.tasksByQuadrant[quadrant] ?: continue
                    item { QuadrantHeader(quadrant, tasks.size) }
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onEditTask(task.id) },
                            onComplete = { viewModel.completeTask(task) },
                            onDelete = { viewModel.deleteTask(task) },
                        )
                    }
                }
                if (uiState.completedTasks.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "Completed · ${uiState.completedTasks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                                letterSpacing = 1.sp,
                            )
                            Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    items(uiState.completedTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { onEditTask(task.id) },
                            onComplete = { },
                            onDelete = { viewModel.deleteTask(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuadrantHeader(quadrant: Quadrant, count: Int) {
    val (colorStart, colorEnd) = quadrantColors(quadrant)
    val (icon, label) = when (quadrant) {
        Quadrant.URGENT_IMPORTANT -> "⚡" to "Now"
        Quadrant.IMPORTANT -> "🎯" to "Next"
        Quadrant.URGENT -> "🔄" to "Soon"
        Quadrant.NEITHER -> "📦" to "Later"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colorStart, colorEnd))),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$icon $label",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colorStart,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colorStart,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colorStart.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `./gradlew assembleDebug --info 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/tasklist/TaskListScreen.kt
git commit -m "feat: redesign task list with Sortd branding, gradient headers, and styled cards"
```

---

### Task 9: Settings Theme Picker

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt:47-48` (add theme section)
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt:18-23,34-50` (add theme state)

- [ ] **Step 1: Add theme mode to SettingsViewModel**

In `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`:

Update `SettingsUiState` (line 18-23) to:
```kotlin
data class SettingsUiState(
    val email: String? = null,
    val availabilities: List<UserAvailability> = emptyList(),
    val calendars: List<CalendarSelection> = emptyList(),
    val syncInterval: SyncInterval = SyncInterval.THIRTY_MINUTES,
    val themeMode: String = "auto",
)
```

Update the `combine` flow (lines 34-50) — change from 4-argument combine to 5-argument:
```kotlin
val uiState: StateFlow<SettingsUiState> = combine(
    authManager.signedInEmail,
    availabilityRepository.observeAll(),
    calendarSelectionRepository.observeAll(),
    appPreferences.syncInterval,
    appPreferences.themeMode,
) { email, availabilities, calendars, interval, theme ->
    SettingsUiState(
        email = email,
        availabilities = availabilities,
        calendars = calendars,
        syncInterval = interval,
        themeMode = theme,
    )
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SettingsUiState(),
)
```

Add function after `signOut` (after line 75):
```kotlin
fun updateThemeMode(mode: String) {
    viewModelScope.launch {
        appPreferences.setThemeMode(mode)
    }
}
```

- [ ] **Step 2: Add theme picker to SettingsScreen**

After `Spacer(Modifier.height(8.dp))` (line 48 in SettingsScreen.kt), add a "Theme" section:
```kotlin
Text("Theme", style = MaterialTheme.typography.titleMedium)
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf("light" to "Light", "dark" to "Dark", "auto" to "Auto").forEach { (mode, label) ->
        val isActive = uiState.themeMode == mode
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier
                .clip(shape)
                .then(
                    if (isActive) Modifier
                        .background(SortdColors.accent.copy(alpha = 0.2f))
                        .border(1.dp, SortdColors.accent, shape)
                    else Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                )
                .clickable { viewModel.updateThemeMode(mode) }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) SortdColors.accent
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}
```

Add required imports: `SortdColors`, `background`, `border`, `clickable`, `clip`, `RoundedCornerShape`, `FontWeight`.

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug --info 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `./gradlew test --info`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt \
       app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt
git commit -m "feat: add theme mode picker (light/dark/auto) to settings"
```

---

### Task 10: Remove Legacy Color Aliases and Clean Up

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/theme/Color.kt` (remove legacy aliases)
- Modify: `app/src/main/java/com/tasktracker/ui/theme/Theme.kt` (remove TaskTrackerTheme alias)
- Modify: `app/src/main/java/com/tasktracker/ui/schedule/ScheduleScreen.kt:108-111` (update color references)
- Modify: `app/src/main/java/com/tasktracker/MainActivity.kt` (rename TaskTrackerTheme → SortdTheme)

- [ ] **Step 1: Remove legacy aliases from Color.kt**

Remove the bottom section of Color.kt (the `// Legacy aliases` block):
```kotlin
// Remove these lines:
val QuadrantUrgentImportant = ...
val QuadrantImportant = ...
val QuadrantUrgent = ...
val QuadrantNeither = ...
val StatusPending = ...
val StatusScheduled = ...
val StatusInProgress = ...
val StatusCompleted = ...
```

- [ ] **Step 2: Update ScheduleScreen.kt color references**

In `app/src/main/java/com/tasktracker/ui/schedule/ScheduleScreen.kt`, replace lines 108-111:
```kotlin
// Old:
Quadrant.URGENT_IMPORTANT -> QuadrantUrgentImportant
Quadrant.IMPORTANT -> QuadrantImportant
Quadrant.URGENT -> QuadrantUrgent
Quadrant.NEITHER -> QuadrantNeither

// New:
Quadrant.URGENT_IMPORTANT -> SortdColors.nowStart
Quadrant.IMPORTANT -> SortdColors.nextStart
Quadrant.URGENT -> SortdColors.soonStart
Quadrant.NEITHER -> SortdColors.laterStart
```

Update the import from `com.tasktracker.ui.theme.*` to include `SortdColors` (or keep the wildcard).

- [ ] **Step 3: Remove TaskTrackerTheme alias from Theme.kt**

Remove the `TaskTrackerTheme` wrapper function. Update `MainActivity.kt` to call `SortdTheme` directly.

- [ ] **Step 4: Search for any remaining references to old color names**

Run: `grep -r "QuadrantUrgentImportant\|QuadrantImportant\|QuadrantUrgent\|QuadrantNeither\|TaskTrackerTheme\|StatusPending\|StatusScheduled\|StatusInProgress\|StatusCompleted" app/src/main/ --include="*.kt"`
Expected: No results (all references should be updated). If any status color references are found in files not rewritten by this plan, update them to use `SortdColors` equivalents before proceeding.

- [ ] **Step 5: Run all tests**

Run: `./gradlew test --info`
Expected: All PASS

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug --info 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: remove legacy color aliases and TaskTrackerTheme wrapper"
```
