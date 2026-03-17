package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimeBlockCard(
    title: String,
    startTime: Instant,
    endTime: Instant,
    color: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    isTaskBlock: Boolean = false,
) {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    val zoneId = ZoneId.systemDefault()
    val startStr = startTime.atZone(zoneId).format(formatter)
    val endStr = endTime.atZone(zoneId).format(formatter)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (isTaskBlock) 0.3f else 0.15f))
            .padding(8.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            Text(
                text = "$startStr - $endStr",
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f),
            )
        }
    }
}
