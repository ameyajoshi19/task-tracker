package com.tasktracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasktracker.domain.model.Tag
import com.tasktracker.ui.theme.SortdColors

val TagColorOptions = listOf(
    0xFFEF4444L, // Red
    0xFFF97316L, // Orange
    0xFFEAB308L, // Yellow
    0xFF22C55EL, // Green
    0xFF14B8A6L, // Teal
    0xFF3B82F6L, // Blue
    0xFF8B5CF6L, // Purple
    0xFFEC4899L, // Pink
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelector(
    tags: List<Tag>,
    selectedTagId: Long?,
    onTagSelected: (Long?) -> Unit,
    onCreateTag: (name: String, color: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateForm by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableLongStateOf(TagColorOptions[0]) }

    val selectedTag = tags.find { it.id == selectedTagId }

    Column(modifier = modifier) {
        Text(
            text = "TAG",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            // Trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .menuAnchor()
                    .clickable { expanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (selectedTag != null) {
                    TagChip(
                        name = selectedTag.name,
                        color = Color(selectedTag.color),
                    )
                } else {
                    Text(
                        text = "No tag",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    showCreateForm = false
                    newTagName = ""
                },
            ) {
                // "No tag" option
                DropdownMenuItem(
                    text = {
                        Text(
                            "No tag",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    onClick = {
                        onTagSelected(null)
                        expanded = false
                    },
                    trailingIcon = {
                        if (selectedTagId == null) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = SortdColors.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )

                // Existing tags
                tags.forEach { tag ->
                    DropdownMenuItem(
                        text = {
                            TagChip(name = tag.name, color = Color(tag.color))
                        },
                        onClick = {
                            onTagSelected(tag.id)
                            expanded = false
                        },
                        trailingIcon = {
                            if (selectedTagId == tag.id) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SortdColors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }

                HorizontalDivider()

                if (!showCreateForm) {
                    // "+ Add new tag" option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SortdColors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Add new tag",
                                    color = SortdColors.accent,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        },
                        onClick = { showCreateForm = true },
                    )
                } else {
                    // Inline create form
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            placeholder = { Text("Tag name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SortdColors.accent,
                            ),
                        )

                        // Color picker row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TagColorOptions.forEach { colorValue ->
                                val isSelected = selectedColor == colorValue
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorValue))
                                        .then(
                                            if (isSelected) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape,
                                            ) else Modifier
                                        )
                                        .clickable { selectedColor = colorValue },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        val checkColor = if (Color(colorValue).luminance() > 0.5f) Color.Black else Color.White
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = checkColor,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Create / Cancel buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showCreateForm = false
                                    newTagName = ""
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (newTagName.isNotBlank()) {
                                        onCreateTag(newTagName.trim(), selectedColor)
                                        newTagName = ""
                                        showCreateForm = false
                                    }
                                },
                                enabled = newTagName.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SortdColors.accent,
                                ),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Create")
                            }
                        }
                    }
                }
            }
        }
    }
}
