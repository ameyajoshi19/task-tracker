package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.R
import com.tasktracker.domain.model.Tag
import com.tasktracker.ui.tasklist.ViewMode
import com.tasktracker.ui.theme.SortdColors

@Composable
fun AppDrawerContent(
    currentViewMode: ViewMode,
    tags: List<Tag>,
    onViewModeSelected: (ViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sortd_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        "sortd",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        ".",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = SortdColors.accent,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Today
            item {
                Spacer(Modifier.height(8.dp))
                DrawerItem(
                    icon = { Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = "Today",
                    isSelected = currentViewMode is ViewMode.Today,
                    onClick = { onViewModeSelected(ViewMode.Today) },
                )
            }

            // All Tasks
            item {
                DrawerItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = "All Tasks",
                    isSelected = currentViewMode is ViewMode.AllTasks,
                    onClick = { onViewModeSelected(ViewMode.AllTasks) },
                )
            }

            // Tags section
            if (tags.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "TAGS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }

                items(tags, key = { it.id }) { tag ->
                    val isSelected = currentViewMode is ViewMode.TagFilter &&
                        currentViewMode.tagId == tag.id
                    DrawerItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.color)),
                            )
                        },
                        label = tag.name,
                        isSelected = isSelected,
                        onClick = { onViewModeSelected(ViewMode.TagFilter(tag.id, tag.name)) },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// Custom DrawerItem used instead of M3 NavigationDrawerItem to match the app's
// existing chip/card styling (accent highlight, rounded corners, consistent spacing)
// which NavigationDrawerItem's opaque container/indicator styling doesn't support well.
@Composable
private fun DrawerItem(
    icon: @Composable () -> Unit,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isSelected) {
        SortdColors.accent.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        SortdColors.accent
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
        )
    }
}
