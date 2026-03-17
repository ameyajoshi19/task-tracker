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
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.ui.theme.*

@Composable
fun QuadrantSelector(
    selected: Quadrant,
    onSelect: (Quadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Priority",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuadrantCell(
                    label = "Urgent &\nImportant",
                    color = QuadrantUrgentImportant,
                    isSelected = selected == Quadrant.URGENT_IMPORTANT,
                    onClick = { onSelect(Quadrant.URGENT_IMPORTANT) },
                    modifier = Modifier.weight(1f),
                )
                QuadrantCell(
                    label = "Important",
                    color = QuadrantImportant,
                    isSelected = selected == Quadrant.IMPORTANT,
                    onClick = { onSelect(Quadrant.IMPORTANT) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuadrantCell(
                    label = "Urgent",
                    color = QuadrantUrgent,
                    isSelected = selected == Quadrant.URGENT,
                    onClick = { onSelect(Quadrant.URGENT) },
                    modifier = Modifier.weight(1f),
                )
                QuadrantCell(
                    label = "Neither",
                    color = QuadrantNeither,
                    isSelected = selected == Quadrant.NEITHER,
                    onClick = { onSelect(Quadrant.NEITHER) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) color else color.copy(alpha = 0.4f),
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
        )
    }
}
