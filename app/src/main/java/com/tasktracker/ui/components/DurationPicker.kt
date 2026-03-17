package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DurationPicker(
    durationMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 15,
    max: Int = 480,
    step: Int = 5,
) {
    Column(modifier = modifier) {
        Text(
            text = "Estimated Duration",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = {
                    val newVal = (durationMinutes - step).coerceAtLeast(min)
                    onDurationChange(newVal)
                },
                enabled = durationMinutes > min,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }

            Text(
                text = formatDuration(durationMinutes),
                style = MaterialTheme.typography.headlineMedium,
            )

            IconButton(
                onClick = {
                    val newVal = (durationMinutes + step).coerceAtMost(max)
                    onDurationChange(newVal)
                },
                enabled = durationMinutes < max,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
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
