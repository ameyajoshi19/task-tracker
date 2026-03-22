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
import com.tasktracker.domain.model.AvailabilitySlotType
import com.tasktracker.ui.theme.SortdColors

@Composable
fun AvailabilitySlotSelector(
    selected: AvailabilitySlotType?,
    enabledSlotTypes: Set<AvailabilitySlotType>,
    onSelect: (AvailabilitySlotType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "AVAILABILITY WINDOW",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Slot type chips — only show types that have at least one enabled day
            for (slotType in AvailabilitySlotType.entries) {
                if (slotType !in enabledSlotTypes) continue
                SlotChip(
                    label = slotType.displayName,
                    isActive = selected == slotType,
                    onClick = { onSelect(slotType) },
                )
            }
            // "Any" chip is always shown
            SlotChip(
                label = "Any",
                isActive = selected == null,
                onClick = { onSelect(null) },
            )
        }
    }
}

@Composable
private fun SlotChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) SortdColors.accent
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
