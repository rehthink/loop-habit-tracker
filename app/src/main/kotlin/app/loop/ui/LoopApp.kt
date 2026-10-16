package app.loop.ui

import androidx.annotation.StringRes
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.loop.MainViewModel
import app.loop.R
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.model.Area
import app.loop.core.model.LoopPermission
import app.loop.core.model.Routes
import app.loop.feature.applock.PinSetupRoute
import app.loop.feature.applock.navigateToPinSetup
import app.loop.feature.applock.pinSetupScreen
import app.loop.feature.checkins.CheckInRoute
import app.loop.feature.checkins.HealthLogRoute
import app.loop.feature.checkins.WeightRoute
import app.loop.feature.checkins.checkInScreens
import app.loop.feature.cycle.CycleRoute
import app.loop.feature.cycle.cycleScreen
import app.loop.feature.food.FoodCameraRoute
import app.loop.feature.food.FoodReviewRoute
import app.loop.feature.food.FoodRoute
import app.loop.feature.food.foodScreens
import app.loop.feature.me.MeLinks
import app.loop.feature.me.MeRoute
import app.loop.feature.me.meScreens
import app.loop.feature.movement.MovementRoute
import app.loop.feature.movement.movementScreen
import app.loop.feature.notifications.NotificationSettingsRoute
import app.loop.feature.notifications.notificationSettingsScreen
import app.loop.feature.onboarding.OnboardingRoute
import app.loop.feature.onboarding.onboardingScreen
import app.loop.feature.permissions.navigateToPermissionDetail
import app.loop.feature.permissions.navigateToPermissions
import app.loop.feature.permissions.permissionsScreens
import app.loop.feature.progress.ProgressRoute
import app.loop.feature.progress.progressScreen
import app.loop.feature.screentime.ScreenTimeRoute
import app.loop.feature.screentime.screenTimeScreen
import app.loop.feature.sleep.SleepRoute
import app.loop.feature.sleep.sleepScreen
import app.loop.feature.today.TodayRoute
import app.loop.feature.today.todayScreen
import kotlinx.coroutines.launch

/** The three bottom-bar tabs. */
enum class TopLevel(val route: Any, val icon: ImageVector, @StringRes val label: Int) {
    TODAY(TodayRoute, LoopIcons.Today, R.string.tab_today),
    PROGRESS(ProgressRoute, LoopIcons.Progress, R.string.tab_progress),
    ME(MeRoute, LoopIcons.Me, R.string.tab_me),
}

@Composable
fun LoopApp(showOnboarding: Boolean, viewModel: MainViewModel) {
    val navController = rememberNavController()
    // Decided once per graph: finishing onboarding recreates the graph (see MainActivity).
    val start: Any = remember { if (showOnboarding) OnboardingRoute else TodayRoute }
    val entry by navController.currentBackStackEntryAsState()
    val current = TopLevel.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.route::class) == true }

    var quickLogOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val waterAdded = stringResource(R.string.quick_log_water_added)
    val undo = stringResource(R.string.quick_log_undo)

    // Deep links from notifications and the widget, once the graph exists and onboarding is done.
    val pendingRoute by viewModel.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRoute, showOnboarding) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (!showOnboarding) navController.openRoute(route)
        viewModel.routeHandled()
    }

    val openPermission: (LoopPermission) -> Unit = { navController.navigateToPermissionDetail(it) }
    val back: () -> Unit = { navController.popBackStack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current != null) BottomBar(current) { navController.navigateToTab(it) }
        },
        floatingActionButton = {
            if (current != null) {
                FloatingActionButton(
                    onClick = { quickLogOpen = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.medium,
                ) { Icon(LoopIcons.Add, contentDescription = stringResource(R.string.quick_log)) }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = start) {
            // Finishing onboarding flips the setting; MainActivity then rebuilds the graph at Today.
            onboardingScreen(onFinished = { })
            todayScreen(
                contentPadding = padding,
                onOpenArea = { area ->
                    when (area) {
                        Area.MOVE -> navController.navigate(MovementRoute)
                        Area.SLEEP -> navController.navigate(SleepRoute)
                        Area.SCREEN -> navController.navigate(ScreenTimeRoute)
                        Area.FUEL -> navController.navigate(FoodRoute)
                    }
                },
                onSetUp = { navController.navigateToPermissions() },
            )
            progressScreen(contentPadding = padding)
            meScreens(
                contentPadding = padding,
                navController = navController,
                links = MeLinks(
                    onOpenPermissions = { navController.navigateToPermissions() },
                    onOpenNotifications = { navController.navigate(NotificationSettingsRoute) },
                    onOpenCheckIn = { navController.navigate(CheckInRoute) },
                    onOpenHealth = { navController.navigate(HealthLogRoute) },
                    onOpenWeight = { navController.navigate(WeightRoute) },
                    onOpenCycle = { navController.navigate(CycleRoute) },
                    onSetUpPin = { navController.navigateToPinSetup() },
                    onEverythingDeleted = viewModel::onEverythingDeleted,
                ),
            )
            permissionsScreens(onBack = back, onOpenDetail = openPermission)
            pinSetupScreen(onDone = back, onBack = back)
            screenTimeScreen(onBack = back, onOpenPermission = openPermission)
            movementScreen(onBack = back, onOpenPermission = openPermission)
            sleepScreen(onBack = back, onOpenPermission = openPermission)
            foodScreens(navController, onOpenPermission = openPermission)
            checkInScreens(onBack = back, onOpenPermission = openPermission)
            notificationSettingsScreen(onBack = back, onOpenPermission = openPermission)
            cycleScreen(onBack = back)
        }
    }

    if (quickLogOpen) {
        QuickLogSheet(
            onDismiss = { quickLogOpen = false },
            onPick = { item ->
                quickLogOpen = false
                when (item) {
                    QuickLogItem.FOOD -> navController.navigate(FoodCameraRoute)
                    QuickLogItem.MOOD -> navController.navigate(CheckInRoute)
                    QuickLogItem.HEALTH -> navController.navigate(HealthLogRoute)
                    QuickLogItem.WATER -> {
                        viewModel.addWater()
                        scope.launch {
                            val result = snackbar.showSnackbar(message = waterAdded, actionLabel = undo)
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoWater()
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun BottomBar(current: TopLevel, onSelect: (TopLevel) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevel.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

private fun NavHostController.navigateToTab(tab: TopLevel) {
    navigate(tab.route) {
        // Today is always the root once onboarding is done.
        popUpTo<TodayRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Maps a deep-link route string (see [Routes]) to a destination. */
private fun NavHostController.openRoute(route: String) {
    val destination: Any = when {
        route == Routes.TODAY -> TodayRoute
        route == Routes.FOOD_CAMERA -> FoodCameraRoute
        route.startsWith(Routes.FOOD_REVIEW_PENDING) ->
            route.removePrefix(Routes.FOOD_REVIEW_PENDING).toLongOrNull()?.let { FoodReviewRoute(pendingId = it) } ?: FoodRoute
        route == Routes.WATER -> FoodRoute
        route == Routes.MOOD -> CheckInRoute
        route == Routes.HEALTH -> HealthLogRoute
        route == Routes.SLEEP -> SleepRoute
        route == Routes.MOVEMENT -> MovementRoute
        route == Routes.SCREEN_TIME -> ScreenTimeRoute
        route == Routes.REPORT -> ProgressRoute
        route == Routes.NOTIFICATION_SETTINGS -> NotificationSettingsRoute
        route == Routes.CYCLE -> CycleRoute
        route == Routes.APP_LOCK -> PinSetupRoute
        else -> return
    }
    if (destination == TodayRoute || destination == ProgressRoute) {
        // A fresh tab, not a restored stack: a deep link should land exactly where it says.
        navigate(destination) {
            popUpTo<TodayRoute> { inclusive = destination == TodayRoute }
            launchSingleTop = true
        }
    } else {
        navigate(destination) { launchSingleTop = true }
    }
}
