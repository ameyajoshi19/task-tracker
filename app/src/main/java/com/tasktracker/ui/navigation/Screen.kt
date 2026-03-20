package com.tasktracker.ui.navigation

sealed class Screen(val route: String) {
    data object SignIn : Screen("sign_in")
    data object Onboarding : Screen("onboarding")
    data object TaskList : Screen("task_list")
    data object TaskEdit : Screen("task_edit/{taskId}") {
        fun createRoute(taskId: Long = -1L) = "task_edit/$taskId"
    }
    data object Schedule : Screen("schedule")
    data object Reschedule : Screen("reschedule")
    data object Settings : Screen("settings")
    data object SettingsAccount : Screen("settings/account")
    data object SettingsAvailability : Screen("settings/availability")
    data object SettingsCalendars : Screen("settings/calendars")
    data object SettingsSync : Screen("settings/sync")
    data object SettingsTheme : Screen("settings/theme")
    data object SettingsDailySummary : Screen("settings/daily_summary")
}
