package com.tasktracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.model.DayPreference

@Composable
fun DayPreferenceSelector(
    selected: DayPreference,
    onSelect: (DayPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Day Preference",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayPreference.entries.forEach { pref ->
                FilterChip(
                    selected = selected == pref,
                    onClick = { onSelect(pref) },
                    label = {
                        Text(
                            when (pref) {
                                DayPreference.WEEKDAY -> "Weekday"
                                DayPreference.WEEKEND -> "Weekend"
                                DayPreference.ANY -> "Any"
                            }
                        )
                    },
                )
            }
        }
    }
}
