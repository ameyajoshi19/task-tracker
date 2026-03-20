// app/src/main/java/com/tasktracker/ui/components/RecurringDeleteDialog.kt
package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RecurringDeleteChoice {
    THIS_INSTANCE,
    THIS_AND_FUTURE,
    ENTIRE_RECURRING_TASK,
}

@Composable
fun RecurringDeleteDialog(
    taskTitle: String,
    intervalDays: Int,
    instanceDate: LocalDate,
    onChoice: (RecurringDeleteChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\uD83D\uDD01", fontSize = 28.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Delete recurring task",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append(taskTitle)
                        append(" repeats every $intervalDays day${if (intervalDays > 1) "s" else ""}.")
                        append("\nWhat would you like to delete?")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(20.dp))

                val dateText = instanceDate.format(DateTimeFormatter.ofPattern("MMM d"))

                // Delete this instance
                DeleteOptionButton(
                    title = "Delete this instance",
                    subtitle = "Only $dateText",
                    backgroundAlpha = 0.15f,
                    borderAlpha = 0.3f,
                    onClick = { onChoice(RecurringDeleteChoice.THIS_INSTANCE) },
                )

                Spacer(Modifier.height(8.dp))

                // Delete this & future
                DeleteOptionButton(
                    title = "Delete this & future",
                    subtitle = "Stop from $dateText onward",
                    backgroundAlpha = 0.08f,
                    borderAlpha = 0.15f,
                    onClick = { onChoice(RecurringDeleteChoice.THIS_AND_FUTURE) },
                )

                Spacer(Modifier.height(8.dp))

                // Delete entire recurring task
                DeleteOptionButton(
                    title = "Delete all instances",
                    subtitle = "Remove this recurring task entirely",
                    backgroundAlpha = 0.05f,
                    borderAlpha = 0.1f,
                    onClick = { onChoice(RecurringDeleteChoice.ENTIRE_RECURRING_TASK) },
                )

                Spacer(Modifier.height(8.dp))

                // Cancel
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        },
    )
}

@Composable
private fun DeleteOptionButton(
    title: String,
    subtitle: String,
    backgroundAlpha: Float,
    borderAlpha: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = backgroundAlpha))
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = borderAlpha),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
        )
    }
}
