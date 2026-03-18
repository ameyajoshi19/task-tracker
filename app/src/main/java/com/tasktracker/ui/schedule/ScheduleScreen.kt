package com.tasktracker.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.domain.model.Quadrant
import com.tasktracker.ui.components.TimeBlockCard
import com.tasktracker.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.viewMode == ViewMode.ALL) {
                            "All Scheduled"
                        } else {
                            uiState.selectedDate.format(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            )
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            if (uiState.viewMode == ViewMode.DAILY) Icons.Default.CalendarViewWeek
                            else Icons.Default.CalendarViewDay,
                            "Toggle view",
                        )

                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.viewMode == ViewMode.DAILY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.navigateDay(false) }) {
                        Icon(Icons.Default.ChevronLeft, "Previous day")
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                SortdColors.accent.copy(alpha = 0.15f),
                                RoundedCornerShape(20.dp),
                            )
                            .border(
                                1.dp,
                                SortdColors.accent.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp),
                            )
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Text(
                            uiState.selectedDate.dayOfWeek.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = SortdColors.accentLight,
                        )
                    }
                    IconButton(onClick = { viewModel.navigateDay(true) }) {
                        Icon(Icons.Default.ChevronRight, "Next day")
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No events scheduled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uiState.items) { item ->
                        val color = if (item.isTaskBlock) {
                            when (item.quadrant) {
                                Quadrant.URGENT_IMPORTANT -> SortdColors.nowStart
                                Quadrant.IMPORTANT -> SortdColors.nextStart
                                Quadrant.URGENT -> SortdColors.soonStart
                                Quadrant.NEITHER -> SortdColors.laterStart
                                null -> MaterialTheme.colorScheme.primary
                            }
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }

                        TimeBlockCard(
                            title = item.title,
                            startTime = item.startTime,
                            endTime = item.endTime,
                            color = color,
                            height = 64.dp,
                            isTaskBlock = item.isTaskBlock,
                            isCompleted = item.isCompleted,
                        )
                    }
                }
            }
        }
    }
}
