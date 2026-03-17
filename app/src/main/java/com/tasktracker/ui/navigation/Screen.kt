package com.tasktracker.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object TaskList : Screen("task_list")
    data object TaskEdit : Screen("task_edit/{taskId}") {
        fun createRoute(taskId: Long = -1L) = "task_edit/$taskId"
    }
    data object Schedule : Screen("schedule")
    data object Reschedule : Screen("reschedule")
    data object Settings : Screen("settings")
}
