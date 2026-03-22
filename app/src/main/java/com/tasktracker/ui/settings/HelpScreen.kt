package com.tasktracker.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.ui.theme.SortdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit,
) {
    var expandedItem by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            HelpItem(
                title = "Getting Started",
                expanded = expandedItem == "getting_started",
                onToggle = { expandedItem = if (expandedItem == "getting_started") null else "getting_started" },
                content = "1. Set up your availability in Settings > Availability. " +
                        "Define your Before Work, During Work, and After Work time windows for each day.\n\n" +
                        "2. Create a task by tapping the + button. Give it a title, set the duration, " +
                        "and pick a priority level.\n\n" +
                        "3. The app will automatically find the best time slot for your task based on " +
                        "your availability and priorities.\n\n" +
                        "4. Connect Google Calendar in Settings > Calendars to avoid double-booking.",
            )

            HelpItem(
                title = "Priority Levels",
                expanded = expandedItem == "priority",
                onToggle = { expandedItem = if (expandedItem == "priority") null else "priority" },
                content = "Tasks are organized using the Eisenhower Matrix:\n\n" +
                        "Now — Urgent & Important. These are scheduled first and get the earliest available slots.\n\n" +
                        "Next — Important but not urgent. High priority but can wait for the best slot.\n\n" +
                        "Soon — Urgent but not important. Scheduled after important tasks.\n\n" +
                        "Later — Neither urgent nor important. Scheduled last with remaining availability.",
            )

            HelpItem(
                title = "Availability Slots",
                expanded = expandedItem == "availability",
                onToggle = { expandedItem = if (expandedItem == "availability") null else "availability" },
                content = "Your day is divided into three time windows:\n\n" +
                        "Before Work — Morning hours before your workday begins.\n\n" +
                        "During Work — Your core working hours.\n\n" +
                        "After Work — Evening hours after work.\n\n" +
                        "Each slot can have different times per day of the week. " +
                        "When creating a task, you can assign it to a specific slot " +
                        "(e.g., personal tasks Before Work, meetings During Work) " +
                        "or leave it as \"Any\" to let the scheduler decide.",
            )

            HelpItem(
                title = "Tags & Filtering",
                expanded = expandedItem == "tags",
                onToggle = { expandedItem = if (expandedItem == "tags") null else "tags" },
                content = "Organize tasks with colored tags like \"Work\" or \"Personal\".\n\n" +
                        "How to create a tag: When creating or editing a task, tap the Tag field, " +
                        "then tap \"Add new tag\". Enter a name, pick a color, and tap Create.\n\n" +
                        "How to filter by tag: Open the navigation drawer (tap the menu icon in the top-left) " +
                        "and select a tag under the Tags section. This shows only tasks with that tag.\n\n" +
                        "The \"All Tasks\" view in the drawer shows every task regardless of tag.",
            )

            HelpItem(
                title = "Today View",
                expanded = expandedItem == "today",
                onToggle = { expandedItem = if (expandedItem == "today") null else "today" },
                content = "The Today view is your default home screen with four sections:\n\n" +
                        "Overdue — Tasks that were scheduled before today or have a past deadline. " +
                        "These need your attention first.\n\n" +
                        "Today — Tasks scheduled for today, plus any tasks with a deadline today.\n\n" +
                        "Upcoming — Tasks scheduled for tomorrow so you can plan ahead.\n\n" +
                        "Completed — Tasks you've finished today.\n\n" +
                        "Use the drawer to switch to \"All Tasks\" for a full view grouped by priority.",
            )

            HelpItem(
                title = "Recurring Tasks",
                expanded = expandedItem == "recurring",
                onToggle = { expandedItem = if (expandedItem == "recurring") null else "recurring" },
                content = "Create tasks that repeat on a regular schedule.\n\n" +
                        "How to set up: When creating a task, toggle \"Recurring\" on. " +
                        "Set the interval (e.g., every 3 days), start date, and optional end date.\n\n" +
                        "Fixed Time: Toggle this on if the task should always happen at a specific time " +
                        "(e.g., 8 PM every day). Fixed-time tasks won't be moved by the scheduler.\n\n" +
                        "Tags applied to any recurring task instance will automatically appear on all " +
                        "instances in the series.",
            )

            HelpItem(
                title = "Scheduling & Rescheduling",
                expanded = expandedItem == "scheduling",
                onToggle = { expandedItem = if (expandedItem == "scheduling") null else "scheduling" },
                content = "The scheduler uses a slot-centric best-fit algorithm:\n\n" +
                        "1. It looks at your available time slots chronologically.\n" +
                        "2. For each slot, it picks the highest-priority task that fits.\n" +
                        "3. This maximizes your time usage — a 1-hour slot gets a 1-hour task, " +
                        "even if a higher-priority 2-hour task exists.\n\n" +
                        "Splittable tasks can be broken across multiple slots (minimum 30 minutes each).\n\n" +
                        "To reschedule a task, swipe it right on the task list. The scheduler will " +
                        "find a new slot while respecting your other commitments.\n\n" +
                        "Pull down on the home screen to sync with Google Calendar and refresh schedules.",
            )

            HelpItem(
                title = "Swipe Actions",
                expanded = expandedItem == "swipe",
                onToggle = { expandedItem = if (expandedItem == "swipe") null else "swipe" },
                content = "Swipe right on a task card to reschedule it. The app will find a new available time slot.\n\n" +
                        "Swipe left on a task card to delete it.\n\n" +
                        "Tap the circle on the right side of a task card to mark it as completed.",
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HelpItem(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            SortdColors.accent.copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = SortdColors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(
                        start = 64.dp,
                        end = 14.dp,
                        bottom = 14.dp,
                    ),
                )
            }
        }
    }
}
