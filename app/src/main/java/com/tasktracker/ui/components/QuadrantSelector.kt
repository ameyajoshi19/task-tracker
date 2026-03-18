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
