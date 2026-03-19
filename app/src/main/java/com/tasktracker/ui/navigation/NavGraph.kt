package com.tasktracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tasktracker.ui.onboarding.OnboardingScreen
import com.tasktracker.ui.signin.SignInScreen
import com.tasktracker.ui.reschedule.RescheduleScreen
import com.tasktracker.ui.schedule.ScheduleScreen
import com.tasktracker.ui.settings.AccountScreen
import com.tasktracker.ui.settings.AvailabilitySettingsScreen
import com.tasktracker.ui.settings.CalendarsScreen
import com.tasktracker.ui.settings.SettingsScreen
import com.tasktracker.ui.settings.SyncScreen
import com.tasktracker.ui.settings.ThemeScreen
import com.tasktracker.ui.taskedit.TaskEditScreen
import com.tasktracker.ui.tasklist.TaskListScreen

@Composable
fun TaskTrackerNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.TaskList.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.TaskList.route) {
            TaskListScreen(
                onAddTask = { navController.navigate(Screen.TaskEdit.createRoute()) },
                onEditTask = { id -> navController.navigate(Screen.TaskEdit.createRoute(id)) },
                onNavigateToSchedule = { navController.navigate(Screen.Schedule.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(
            route = Screen.TaskEdit.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            TaskEditScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReschedule = { navController.navigate(Screen.Reschedule.route) },
            )
        }
        composable(Screen.Schedule.route) {
            ScheduleScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Reschedule.route) {
            RescheduleScreen(
                onNavigateBack = {
                    navController.popBackStack(Screen.TaskList.route, inclusive = false)
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToAccount = { navController.navigate(Screen.SettingsAccount.route) },
                onNavigateToAvailability = { navController.navigate(Screen.SettingsAvailability.route) },
                onNavigateToCalendars = { navController.navigate(Screen.SettingsCalendars.route) },
                onNavigateToSync = { navController.navigate(Screen.SettingsSync.route) },
                onNavigateToTheme = { navController.navigate(Screen.SettingsTheme.route) },
            )
        }
        composable(Screen.SettingsAccount.route) {
            AccountScreen(
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.SettingsAvailability.route) {
            AvailabilitySettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.SettingsCalendars.route) {
            CalendarsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.SettingsSync.route) {
            SyncScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.SettingsTheme.route) {
            ThemeScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
