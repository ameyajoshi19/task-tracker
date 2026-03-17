package com.tasktracker.domain.model

enum class SyncInterval(val minutes: Long, val label: String) {
    FIFTEEN_MINUTES(15, "15 minutes"),
    THIRTY_MINUTES(30, "30 minutes"),
    ONE_HOUR(60, "1 hour"),
    MANUAL(0, "Manual only");
}
