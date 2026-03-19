# Login Page, Settings Restructure & Availability Editor Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the onboarding sign-in step with a standalone branded login page, restructure Settings into hub-and-spoke navigation, and polish the availability editor with card-based styling and a "Copy to all" feature.

**Architecture:** The sign-in screen becomes its own route and ViewModel, decoupled from onboarding. Settings becomes a hub screen linking to 5 sub-pages via new nav routes. The availability editor gets visual polish and a new `onCopyToAll` callback backed by a new DAO/repository method.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, Hilt, Google Sign-In, Compose Navigation.

---

### Task 1: Add "Copy to All" to Data Layer

**Files:**
- Modify: `app/src/main/java/com/tasktracker/data/local/dao/UserAvailabilityDao.kt`
- Modify: `app/src/main/java/com/tasktracker/domain/repository/UserAvailabilityRepository.kt`
- Modify: `app/src/main/java/com/tasktracker/data/repository/UserAvailabilityRepositoryImpl.kt`

- [ ] **Step 1: Add DAO queries for copy-to-all**

In `UserAvailabilityDao.kt`, add two new queries:

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAll(availabilities: List<UserAvailabilityEntity>)

@Query("SELECT * FROM user_availability WHERE dayOfWeek = :dayOfWeek AND enabled = 1 ORDER BY startTime")
suspend fun getByDayOfWeek(dayOfWeek: DayOfWeek): List<UserAvailabilityEntity>

@Query("DELETE FROM user_availability WHERE dayOfWeek != :dayOfWeek")
suspend fun deleteAllExceptDay(dayOfWeek: DayOfWeek)
```

- [ ] **Step 2: Add repository interface method**

In `UserAvailabilityRepository.kt`, add:

```kotlin
suspend fun copyToAllDays(sourceDayOfWeek: DayOfWeek)
```

- [ ] **Step 3: Implement repository method**

In `UserAvailabilityRepositoryImpl.kt`, add:

```kotlin
@androidx.room.Transaction
override suspend fun copyToAllDays(sourceDayOfWeek: DayOfWeek) {
    val sourceSlots = dao.getByDayOfWeek(sourceDayOfWeek)
    dao.deleteAllExceptDay(sourceDayOfWeek)
    val copies = DayOfWeek.entries
        .filter { it != sourceDayOfWeek }
        .flatMap { targetDay ->
            sourceSlots.map { slot ->
                slot.copy(id = 0, dayOfWeek = targetDay)
            }
        }
    dao.insertAll(copies)
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tasktracker/data/local/dao/UserAvailabilityDao.kt \
      app/src/main/java/com/tasktracker/domain/repository/UserAvailabilityRepository.kt \
      app/src/main/java/com/tasktracker/data/repository/UserAvailabilityRepositoryImpl.kt
git commit -m "feat: add copyToAllDays to availability data layer"
```

---

### Task 2: Polish AvailabilityEditor with Card-Based Styling and Copy Button

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt`

This is a visual rewrite of the existing component. The data contract (callbacks) stays the same, with one addition: `onCopyToAll`.

- [ ] **Step 1: Add `onCopyToAll` callback and update the main composable signature**

Update the `AvailabilityEditor` function signature:

```kotlin
@Composable
fun AvailabilityEditor(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    onAdd: (UserAvailability) -> Unit = {},
    onRemove: (UserAvailability) -> Unit = {},
    onCopyToAll: (DayOfWeek) -> Unit = {},
    modifier: Modifier = Modifier,
)
```

Pass `onCopyToAll` through to `AvailabilityDayGroup`.

- [ ] **Step 2: Rewrite `AvailabilityDayGroup` with card styling**

Replace the current flat `Row` layout with a card-based layout. Each day is wrapped in a `Surface` with:
- Background: `SortdColors.Dark.card` (`#231E30`)
- Border: `1.dp` of `SortdColors.Dark.border` (`#3D3455`)
- Corner radius: `12.dp`
- Padding: `12.dp`

Header row contains: toggle + day name (abbreviated, bold) on the left, "Copy to all" pill button on the right (only when enabled).

The "Copy to all" pill button:
- Background: `SortdColors.accent.copy(alpha = 0.1f)`
- Corner radius: `6.dp`
- Padding: `4.dp` vertical, `8.dp` horizontal
- Icon: `Icons.Default.ContentCopy` at `12.dp`
- Text: "Copy to all" at `11.sp`, color `SortdColors.accentLight`

Disabled days: wrap the card in `Modifier.alpha(0.6f)`, show only toggle + day name.

- [ ] **Step 3: Rewrite `TimeSlotRow` with bordered chips**

Replace the plain `TextButton` time displays with bordered chip containers:

```kotlin
Surface(
    shape = RoundedCornerShape(6.dp),
    color = SortdColors.Dark.background,
    border = BorderStroke(1.dp, SortdColors.Dark.border),
    modifier = Modifier.clickable { showStartPicker = true },
) {
    Text(
        text = slot.startTime.toString(),
        style = MaterialTheme.typography.bodySmall,
        color = SortdColors.Dark.textPrimary,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
```

Between start and end chips, show a "to" label in `SortdColors.Dark.textTertiary`.

Keep the existing `AlertDialog` time pickers unchanged.

- [ ] **Step 4: Add Snackbar feedback for copy-to-all**

The `AvailabilityEditor` needs to show a Snackbar when "Copy to all" is tapped. Add a `snackbarHostState` parameter (optional, defaults to `null`) or manage it internally with `remember { SnackbarHostState() }`. When the copy button is clicked, call `onCopyToAll(day)` and then launch a coroutine to show the Snackbar:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
```

In the "Copy to all" button's `onClick`:

```kotlin
onClick = {
    onCopyToAll(day)
    scope.launch {
        snackbarHostState.showSnackbar(
            "${dayName}'s schedule copied to all days"
        )
    }
}
```

Add a `SnackbarHost(snackbarHostState)` at the bottom of the editor's `Column`, or wrap the content in a `Box` with the `SnackbarHost` overlaid at the bottom.

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/components/AvailabilityEditor.kt
git commit -m "feat: polish availability editor with card styling and copy-to-all button"
```

---

### Task 3: Create Standalone Sign-In Screen

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/signin/SignInScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/signin/SignInViewModel.kt`

- [ ] **Step 1: Create `SignInViewModel`**

Create `app/src/main/java/com/tasktracker/ui/signin/SignInViewModel.kt`:

```kotlin
package com.tasktracker.ui.signin

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.tasktracker.data.calendar.GoogleAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    val authManager: GoogleAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    fun setSigningIn() {
        _uiState.update { it.copy(isSigningIn = true, signInError = null) }
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val result = authManager.handleSignInResult(account)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSigningIn = false, signedIn = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSigningIn = false, signInError = error.message)
                    }
                },
            )
        } catch (e: ApiException) {
            _uiState.update {
                it.copy(isSigningIn = false, signInError = "Sign-in failed: ${e.statusCode}")
            }
        }
    }
}
```

- [ ] **Step 2: Create `SignInScreen`**

Create `app/src/main/java/com/tasktracker/ui/signin/SignInScreen.kt`:

This is a full-screen composable with:
- `Box(Modifier.fillMaxSize().background(gradientBrush))` where `gradientBrush` is `Brush.linearGradient(listOf(Color(0xFF1A1625), Color(0xFF2D2640)))` at 135 degrees
- Center-aligned content: logo `Icon` (load `ic_sortd_logo` as a painter), "Sortd" text (32sp, bold, `#F1F5F9`), tagline "Smart scheduling, sorted." (14sp, `#A78BFA`, medium weight)
- Logo container: `Box` with `80.dp` size, `RoundedCornerShape(20.dp)`, background `SortdColors.accent.copy(alpha = 0.15f)`
- Bottom-anchored column (`Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)`):
  - Pill-shaped button: `Surface(color = Color.White, shape = RoundedCornerShape(20.dp))` containing a `Row` with the Google `G` icon (use a `Canvas` or small inline painter — 16dp) and "Sign in with Google" text (13sp, `#333`, medium weight). Horizontal padding 24dp, vertical 10dp. Loading state: show `CircularProgressIndicator(modifier = Modifier.size(16.dp))` instead of Google icon.
  - Privacy text below: `ClickableText` or `Text` with `AnnotatedString` — "By signing in, you agree to our " + underlined "Privacy Policy" link. Both in color `Color(0xFF555555)`, size 11sp. Clicking "Privacy Policy" opens `https://ameyajoshi19.github.io/task-tracker/privacy-policy.html` in browser via `context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))`.
  - Error text: if `error != null`, show it in `MaterialTheme.colorScheme.error`

Wire up `rememberLauncherForActivityResult` for Google Sign-In (same pattern as current `OnboardingScreen`).

Use `LaunchedEffect(uiState.signedIn)` — when `true`, call `onSignedIn()`.

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/signin/
git commit -m "feat: create standalone branded sign-in screen"
```

---

### Task 4: Update Navigation — Add SignIn Route and Remove Sign-In from Onboarding

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/tasktracker/MainActivity.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt`

- [ ] **Step 1: Add `SignIn` route to `Screen.kt`**

```kotlin
data object SignIn : Screen("sign_in")
```

- [ ] **Step 2: Update `NavGraph.kt`**

Add the `SignIn` composable destination before the `Onboarding` one:

```kotlin
composable(Screen.SignIn.route) {
    SignInScreen(
        onSignedIn = {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(Screen.SignIn.route) { inclusive = true }
            }
        },
    )
}
```

Add the import for `SignInScreen`.

Also update the Settings sign-out navigation to go to `Screen.SignIn.route` instead of `Screen.Onboarding.route`:

```kotlin
onSignedOut = {
    navController.navigate(Screen.SignIn.route) {
        popUpTo(0) { inclusive = true }
    }
},
```

- [ ] **Step 3: Update `MainActivity.kt` routing with three-way check**

The spec requires three routing states: unauthenticated → `SignIn`, authenticated + onboarding incomplete → `Onboarding`, authenticated + onboarding complete → `TaskList`.

Add a `GoogleAuthManager` injection to `MainActivity` and check auth state:

```kotlin
@Inject
lateinit var authManager: GoogleAuthManager
```

Update the `when` block:

```kotlin
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
```

This way a user who signed in then killed the app mid-onboarding will resume at `Onboarding`, not `SignIn`.

- [ ] **Step 4: Remove `SIGN_IN` from onboarding**

In `OnboardingViewModel.kt`:
- Change enum: `enum class OnboardingStep { AVAILABILITY, CALENDARS, DONE }`
- Change default state: `val step: OnboardingStep = OnboardingStep.AVAILABILITY`
- Remove `handleSignInResult()`, `setSigningIn()`, `getSignInIntent()` functions
- Remove `isSigningIn` and `signInError` fields from `OnboardingUiState`
- Remove `email` field from `OnboardingUiState`

In `OnboardingScreen.kt`:
- Remove the `signInLauncher` and `SignInStep` composable
- Remove the `OnboardingStep.SIGN_IN` branch from the `when` block
- Update the progress indicator: `AVAILABILITY -> 0.5f`, `CALENDARS -> 1.0f`, `DONE -> 1.0f`

- [ ] **Step 5: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/navigation/Screen.kt \
      app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt \
      app/src/main/java/com/tasktracker/MainActivity.kt \
      app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt \
      app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt
git commit -m "feat: route sign-in to standalone screen, remove from onboarding"
```

---

### Task 5: Restructure Settings into Hub Screen

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add computed subtitle values to `SettingsUiState`**

In `SettingsViewModel.kt`, add computed properties to `SettingsUiState`:

```kotlin
data class SettingsUiState(
    val email: String? = null,
    val displayName: String? = null,
    val availabilities: List<UserAvailability> = emptyList(),
    val calendars: List<CalendarSelection> = emptyList(),
    val syncInterval: SyncInterval = SyncInterval.THIRTY_MINUTES,
    val themeMode: String = "auto",
) {
    val activeDayCount: Int
        get() = availabilities.filter { it.enabled }.map { it.dayOfWeek }.distinct().size
    val syncedCalendarCount: Int
        get() = calendars.count { it.enabled }
    val themeModeLabel: String
        get() = themeMode.replaceFirstChar { it.uppercase() }
}
```

Also add `copyToAllDays` method:

```kotlin
fun copyToAllDays(dayOfWeek: DayOfWeek) {
    viewModelScope.launch {
        availabilityRepository.copyToAllDays(dayOfWeek)
    }
}
```

- [ ] **Step 2: Rewrite `SettingsScreen` as a hub menu**

Replace the entire `SettingsScreen` content (below the Scaffold/TopAppBar) with a `Column` of card rows. Each row is a `Surface` with `SortdColors.Dark.card` background, `12.dp` corners, `1.dp` border, containing:
- Left: colored icon in a `Box(Modifier.size(36.dp).background(tintColor, RoundedCornerShape(10.dp)))`
- Center: title `Text` (14sp, medium) + subtitle `Text` (11sp, secondary color)
- Right: chevron icon (`Icons.AutoMirrored.Filled.KeyboardArrowRight`)

Row click navigates to the corresponding sub-page route.

The `SettingsScreen` signature gains navigation callbacks:

```kotlin
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToAvailability: () -> Unit,
    onNavigateToCalendars: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToTheme: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
)
```

Use Material 3 icons: `Icons.Outlined.Person`, `Icons.Outlined.Schedule`, `Icons.Outlined.CalendarMonth`, `Icons.Outlined.Sync`, `Icons.Outlined.DarkMode`. Import from `androidx.compose.material.icons.outlined.*`.

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (sub-pages not yet created, but SettingsScreen compiles)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/SettingsScreen.kt \
      app/src/main/java/com/tasktracker/ui/settings/SettingsViewModel.kt
git commit -m "feat: rewrite settings as hub menu with computed subtitles"
```

---

### Task 6: Create Settings Sub-Pages

**Files:**
- Create: `app/src/main/java/com/tasktracker/ui/settings/AccountScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/settings/AvailabilitySettingsScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/settings/CalendarsScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/settings/SyncScreen.kt`
- Create: `app/src/main/java/com/tasktracker/ui/settings/ThemeScreen.kt`

Each sub-page shares the `SettingsViewModel` (injected via `hiltViewModel()`) and uses a `Scaffold` with a `TopAppBar` containing a back arrow and the section title.

- [ ] **Step 1: Create `AccountScreen.kt`**

Extract the Account card content from the old `SettingsScreen`. Shows signed-in name, email, and a "Sign out" button. The `onSignedOut` callback is passed through.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
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
                .padding(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Signed in as",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        uiState.displayName?.let { name ->
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            uiState.email ?: "Not signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.displayName != null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    TextButton(onClick = {
                        viewModel.signOut(context)
                        onSignedOut()
                    }) {
                        Text("Sign out")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create `AvailabilitySettingsScreen.kt`**

Wraps `AvailabilityEditor` in a Scaffold with TopAppBar. Passes all callbacks from `SettingsViewModel` plus `onCopyToAll`.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilitySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Availability") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        AvailabilityEditor(
            availabilities = uiState.availabilities,
            onUpdate = viewModel::updateAvailability,
            onAdd = viewModel::addAvailability,
            onRemove = viewModel::removeAvailability,
            onCopyToAll = viewModel::copyToAllDays,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        )
    }
}
```

- [ ] **Step 3: Create `CalendarsScreen.kt`**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendars") },
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
                .padding(16.dp),
        ) {
            Text(
                "Select which calendars to check for scheduling conflicts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            for (cal in uiState.calendars) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = cal.enabled,
                        onCheckedChange = { viewModel.toggleCalendar(cal) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(cal.calendarName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Create `SyncScreen.kt`**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Background Sync") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedTextField(
                    value = uiState.syncInterval.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sync Interval") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    SyncInterval.entries.forEach { interval ->
                        DropdownMenuItem(
                            text = { Text(interval.label) },
                            onClick = {
                                viewModel.updateSyncInterval(interval)
                                expanded = false
                            },
                        )
                    }
                }
            }
            Text(
                "More frequent syncs keep your schedule up to date but use more battery in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 5: Create `ThemeScreen.kt`**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
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
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Light", "Dark", "Auto").forEach { label ->
                    val mode = label.lowercase()
                    val isActive = uiState.themeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isActive) SortdColors.accent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) SortdColors.accent
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable { viewModel.updateThemeMode(mode) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) SortdColors.accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/settings/AccountScreen.kt \
      app/src/main/java/com/tasktracker/ui/settings/AvailabilitySettingsScreen.kt \
      app/src/main/java/com/tasktracker/ui/settings/CalendarsScreen.kt \
      app/src/main/java/com/tasktracker/ui/settings/SyncScreen.kt \
      app/src/main/java/com/tasktracker/ui/settings/ThemeScreen.kt
git commit -m "feat: create settings sub-pages for account, availability, calendars, sync, theme"
```

---

### Task 7: Wire Up Settings Sub-Page Navigation

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Add 5 new routes to `Screen.kt`**

```kotlin
data object SettingsAccount : Screen("settings/account")
data object SettingsAvailability : Screen("settings/availability")
data object SettingsCalendars : Screen("settings/calendars")
data object SettingsSync : Screen("settings/sync")
data object SettingsTheme : Screen("settings/theme")
```

- [ ] **Step 2: Add composable destinations to `NavGraph.kt`**

After the existing `Screen.Settings` composable, add:

```kotlin
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
```

Update the `SettingsScreen` composable to pass navigation callbacks:

```kotlin
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
```

Add all necessary imports for the new screens.

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/navigation/Screen.kt \
      app/src/main/java/com/tasktracker/ui/navigation/NavGraph.kt
git commit -m "feat: wire up settings sub-page navigation routes"
```

---

### Task 8: Wire Copy-to-All into Onboarding

**Files:**
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt`

- [ ] **Step 1: Add `copyToAllDays` to `OnboardingViewModel`**

Since onboarding uses in-memory state (not yet persisted to DB), the copy-to-all operates on the local `availabilities` list:

```kotlin
fun copyToAllDays(sourceDayOfWeek: DayOfWeek) {
    _uiState.update { state ->
        val sourceSlots = state.availabilities.filter {
            it.dayOfWeek == sourceDayOfWeek && it.enabled
        }
        val otherDays = DayOfWeek.entries.filter { it != sourceDayOfWeek }
        val copied = otherDays.flatMap { targetDay ->
            sourceSlots.mapIndexed { index, slot ->
                val newId = (state.availabilities.minOfOrNull { it.id } ?: 0L) - 1 - (otherDays.indexOf(targetDay) * sourceSlots.size + index)
                slot.copy(id = newId, dayOfWeek = targetDay)
            }
        }
        val updatedList = state.availabilities.filter { it.dayOfWeek == sourceDayOfWeek } + copied
        state.copy(availabilities = updatedList)
    }
}
```

- [ ] **Step 2: Pass `onCopyToAll` in `OnboardingScreen`**

In the `AvailabilityStep` composable call, add the callback:

```kotlin
OnboardingStep.AVAILABILITY -> AvailabilityStep(
    availabilities = uiState.availabilities,
    onUpdate = viewModel::updateAvailability,
    onAdd = viewModel::addAvailability,
    onRemove = viewModel::removeAvailability,
    onCopyToAll = viewModel::copyToAllDays,
    onNext = viewModel::saveAvailabilityAndProceed,
)
```

Update the `AvailabilityStep` composable signature and pass it through to `AvailabilityEditor`:

```kotlin
@Composable
private fun AvailabilityStep(
    availabilities: List<UserAvailability>,
    onUpdate: (UserAvailability) -> Unit,
    onAdd: (UserAvailability) -> Unit,
    onRemove: (UserAvailability) -> Unit,
    onCopyToAll: (DayOfWeek) -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AvailabilityEditor(
            availabilities = availabilities,
            onUpdate = onUpdate,
            onAdd = onAdd,
            onRemove = onRemove,
            onCopyToAll = onCopyToAll,
            modifier = Modifier.weight(1f),
        )
        // ... rest unchanged
    }
}
```

- [ ] **Step 3: Build to verify full app compilation**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/tasktracker/ui/onboarding/OnboardingViewModel.kt \
      app/src/main/java/com/tasktracker/ui/onboarding/OnboardingScreen.kt
git commit -m "feat: wire copy-to-all availability into onboarding flow"
```

---

### Task 9: Run Tests and Final Verification

**Files:** None (verification only)

- [ ] **Step 1: Run existing unit tests**

Run: `./gradlew test`
Expected: All tests pass. If any fail, fix them — likely the `OnboardingStep.SIGN_IN` removal may affect tests that reference it.

- [ ] **Step 2: Build release to verify ProGuard compatibility**

Run: `./gradlew assembleRelease`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit any test fixes**

If tests needed fixing:
```bash
git add -A
git commit -m "fix: update tests for sign-in and settings restructure"
```
