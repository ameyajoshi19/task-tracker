package com.tasktracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.tasktracker.data.connectivity.ConnectivityObserver
import com.tasktracker.data.calendar.GoogleAuthManager
import com.tasktracker.data.preferences.AppPreferences
import com.tasktracker.data.sync.SyncScheduler
import com.tasktracker.ui.navigation.Screen
import com.tasktracker.ui.navigation.TaskTrackerNavGraph
import com.tasktracker.ui.theme.SortdTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var authManager: GoogleAuthManager

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* No action needed — user's choice is respected */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        // Trigger sync on app open (only if onboarding complete)
        lifecycleScope.launch {
            if (appPreferences.onboardingCompleted.first()) {
                syncScheduler.syncNow()
            }
        }

        // Sync on connectivity restored
        lifecycleScope.launch {
            connectivityObserver.observe()
                .distinctUntilChanged()
                .filter { isConnected -> isConnected }
                .collect {
                    if (appPreferences.onboardingCompleted.first()) {
                        syncScheduler.syncNow()
                    }
                }
        }

        // Read deep link intent
        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            val themeMode by appPreferences.themeMode
                .collectAsState(initial = "auto")
            SortdTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val onboardingCompleted by appPreferences.onboardingCompleted
                    .collectAsState(initial = null)

                // Handle deep link navigation from notifications
                LaunchedEffect(onboardingCompleted, navigateTo) {
                    if (onboardingCompleted == true && navigateTo == "reschedule") {
                        navController.navigate(Screen.Reschedule.route)
                    }
                }

                when (onboardingCompleted) {
                    null -> { /* Loading */ }
                    true -> TaskTrackerNavGraph(
                        navController = navController,
                        startDestination = Screen.TaskList.route,
                    )
                    false -> {
                        val isSignedIn = authManager.isSignedIn
                        TaskTrackerNavGraph(
                            navController = navController,
                            startDestination = if (isSignedIn) Screen.Onboarding.route else Screen.SignIn.route,
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
