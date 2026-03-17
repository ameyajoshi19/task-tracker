package com.tasktracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun TaskTrackerNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Onboarding.route) {
            PlaceholderScreen("Onboarding")
        }
        composable(Screen.TaskList.route) {
            PlaceholderScreen("Task List")
        }
        composable(
            route = Screen.TaskEdit.route,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: -1L
            PlaceholderScreen("Task Edit (id=$taskId)")
        }
        composable(Screen.Schedule.route) {
            PlaceholderScreen("Schedule")
        }
        composable(Screen.Reschedule.route) {
            PlaceholderScreen("Reschedule")
        }
        composable(Screen.Settings.route) {
            PlaceholderScreen("Settings")
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}
