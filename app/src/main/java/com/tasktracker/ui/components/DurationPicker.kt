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
    listOf("prototype", "mockup") to 120,
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
