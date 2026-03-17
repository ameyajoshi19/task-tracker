package com.tasktracker.domain.model

enum class Quadrant(val priority: Int) {
    URGENT_IMPORTANT(0),
    IMPORTANT(1),
    URGENT(2),
    NEITHER(3);
}
