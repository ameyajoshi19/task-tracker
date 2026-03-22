package com.tasktracker.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tasktracker.ui.theme.SortdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToAvailability: () -> Unit,
    onNavigateToCalendars: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToDailySummary: () -> Unit,
    onNavigateToHelp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsRow(
                icon = Icons.Outlined.Person,
                iconColor = SortdColors.accentLight,
                tintBg = SortdColors.accent.copy(alpha = 0.15f),
                title = "Account",
                subtitle = uiState.email ?: "Not signed in",
                onClick = onNavigateToAccount,
            )

            SettingsRow(
                icon = Icons.Outlined.Schedule,
                iconColor = Color(0xFF34D399),
                tintBg = Color(0xFF34D399).copy(alpha = 0.15f),
                title = "Availability",
                subtitle = "${uiState.activeDayCount} days active",
                onClick = onNavigateToAvailability,
            )

            SettingsRow(
                icon = Icons.Outlined.CalendarMonth,
                iconColor = Color(0xFFEC4899),
                tintBg = Color(0xFFEC4899).copy(alpha = 0.15f),
                title = "Calendars",
                subtitle = "${uiState.syncedCalendarCount} calendars synced",
                onClick = onNavigateToCalendars,
            )

            SettingsRow(
                icon = Icons.Outlined.Notifications,
                iconColor = Color(0xFFF59E0B),
                tintBg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                title = "Daily Summary",
                subtitle = uiState.dailySummarySubtitle,
                onClick = onNavigateToDailySummary,
            )

            SettingsRow(
                icon = Icons.Outlined.Sync,
                iconColor = Color(0xFFF59E0B),
                tintBg = Color(0xFFF59E0B).copy(alpha = 0.15f),
                title = "Background Sync",
                subtitle = uiState.syncInterval.label,
                onClick = onNavigateToSync,
            )

            SettingsRow(
                icon = Icons.Outlined.DarkMode,
                iconColor = Color(0xFF8B5CF6),
                tintBg = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                title = "Theme",
                subtitle = uiState.themeModeLabel,
                onClick = onNavigateToTheme,
            )

            SettingsRow(
                icon = Icons.Outlined.HelpOutline,
                iconColor = SortdColors.accent,
                tintBg = SortdColors.accent.copy(alpha = 0.15f),
                title = "Help",
                subtitle = "Features & how-tos",
                onClick = onNavigateToHelp,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconColor: Color,
    tintBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tintBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
