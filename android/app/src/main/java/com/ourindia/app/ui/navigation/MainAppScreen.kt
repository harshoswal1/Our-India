package com.ourindia.app.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ourindia.app.ui.onboarding.LOCATION_PERMISSIONS
import com.ourindia.app.ui.onboarding.LocationPromptDialog
import com.ourindia.app.ui.onboarding.TutorialDialog
import com.ourindia.app.ui.onboarding.canAskLocationAgain
import com.ourindia.app.ui.onboarding.hasLocationPermission
import com.ourindia.app.ui.onboarding.openAppSettings
import com.ourindia.app.ui.onboarding.rememberFirstRunPrefs
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.ui.civic.AuthScreen
import com.ourindia.app.ui.civic.IssueDetailScreen
import com.ourindia.app.ui.civic.IssuesScreen
import com.ourindia.app.ui.civic.ReportScreen
import com.ourindia.app.ui.home.HomeScreen
import com.ourindia.app.ui.me.MeScreen
import com.ourindia.app.ui.partystructure.PartyStructureScreen
import com.ourindia.app.ui.theme.CivicColors

private object Routes {
    const val HOME = "home"
    const val LEADERS = "leaders?party={party}&node={node}"
    const val ISSUES = "issues"
    const val ME = "me"
    const val REPORT = "report"
    const val ISSUE = "issue/{id}"
    const val AUTH = "auth"

    fun leaders(party: String? = null, node: String? = null): String = when {
        party == null -> "leaders"
        node == null -> "leaders?party=${android.net.Uri.encode(party)}"
        else -> "leaders?party=${android.net.Uri.encode(party)}&node=${android.net.Uri.encode(node)}"
    }
    fun issue(id: String) = "issue/$id"
}

private data class Tab(val route: String, val base: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val civic: Boolean = false)

private val tabs = listOf(
    Tab(Routes.HOME, "home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.LEADERS, "leaders", "Leaders", Icons.Outlined.AccountTree, Icons.Filled.AccountTree),
    Tab(Routes.REPORT, "report", "Report", Icons.Outlined.Add, Icons.Outlined.Add, civic = true),
    Tab(Routes.ISSUES, "issues", "Issues", Icons.Outlined.Inbox, Icons.Filled.Inbox, civic = true),
    Tab(Routes.ME, "me", "You", Icons.Outlined.Person, Icons.Filled.Person)
)

@Composable
fun MainAppScreen() {
    val nav = rememberNavController()
    val context = LocalContext.current
    // One resolver for the app: it caches the parsed boundary files every screen uses.
    val resolver = remember { LocalGeographicResolver(context.applicationContext) }
    var leadersImmersive by remember { mutableStateOf(false) }

    // ── First launch: tutorial, then the location prompt; later launches nudge while denied.
    val firstRun = rememberFirstRunPrefs()
    var showTutorial by remember { mutableStateOf(!firstRun.tutorialSeen) }
    var locationAllowed by remember { mutableStateOf(hasLocationPermission(context)) }
    var promptLocation by rememberSaveable { mutableStateOf(firstRun.tutorialSeen && !locationAllowed && !firstRun.locationNeverAsk) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        firstRun.locationAsked = true
        locationAllowed = grants.values.any { it }
    }
    fun askForLocation() {
        if (canAskLocationAgain(context as? Activity, firstRun)) permission.launch(LOCATION_PERMISSIONS) else openAppSettings(context)
    }
    // Coming back from Settings with the permission granted.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) locationAllowed = hasLocationPermission(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val crashFile = remember { java.io.File(context.filesDir, com.ourindia.app.OurIndiaApplication.CRASH_FILE) }
    var lastCrash by remember { mutableStateOf(crashFile.takeIf { it.exists() }?.readText()) }
    lastCrash?.let { report ->
        AlertDialog(
            onDismissRequest = { crashFile.delete(); lastCrash = null },
            title = { Text("Our India closed unexpectedly") },
            text = { Text("Sharing the error report helps us fix it. It holds only the technical error and your phone model.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report), "Share error report"))
                    crashFile.delete(); lastCrash = null
                }) { Text("Share report") }
            },
            dismissButton = { TextButton(onClick = { crashFile.delete(); lastCrash = null }) { Text("Dismiss") } }
        )
    }
    if (lastCrash != null) Unit else if (showTutorial) {
        TutorialDialog(onFinish = {
            firstRun.tutorialSeen = true
            showTutorial = false
            if (!locationAllowed) askForLocation()
        })
    } else if (promptLocation && !locationAllowed) {
        LocationPromptDialog(
            onAllow = { promptLocation = false; askForLocation() },
            onNotNow = { promptLocation = false },
            onNever = { promptLocation = false; firstRun.locationNeverAsk = true }
        )
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val onTab = route in setOf(Routes.HOME, Routes.ISSUES, Routes.ME) || (route == Routes.LEADERS && !leadersImmersive)

    Scaffold(
        bottomBar = { if (onTab) BottomBar(nav, route) },
        containerColor = CivicColors.Background
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            modifier = Modifier
                .padding(inner)
                // The bar already took the gesture-nav inset; don't let inner scaffolds add it twice.
                .consumeWindowInsets(inner)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    resolver = resolver,
                    locationAllowed = locationAllowed,
                    onOpenIssue = { nav.navigate(Routes.issue(it)) },
                    onReport = { nav.navigate(Routes.REPORT) },
                    onNeedsSignIn = { nav.navigate(Routes.AUTH) }
                )
            }
            composable(
                Routes.LEADERS,
                arguments = listOf(
                    navArgument("party") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("node") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { e ->
                PartyStructureScreen(
                    resolver = resolver,
                    openParty = e.arguments?.getString("party"),
                    openNodeId = e.arguments?.getString("node"),
                    onImmersiveChange = { leadersImmersive = it }
                )
            }
            composable(Routes.ISSUES) {
                IssuesScreen(
                    onOpenIssue = { nav.navigate(Routes.issue(it)) },
                    onReport = { nav.navigate(Routes.REPORT) },
                    onNeedsSignIn = { nav.navigate(Routes.AUTH) }
                )
            }
            composable(Routes.ME) {
                MeScreen(onSignIn = { nav.navigate(Routes.AUTH) }, onOpenIssue = { nav.navigate(Routes.issue(it)) }, onShowTutorial = { showTutorial = true })
            }
            composable(Routes.REPORT) {
                ReportScreen(
                    resolver = resolver,
                    onClose = { nav.popBackStack() },
                    onOpenIssue = { id -> nav.navigate(Routes.issue(id)) { popUpTo(Routes.REPORT) { inclusive = true } } },
                    onNeedsSignIn = { nav.navigate(Routes.AUTH) }
                )
            }
            composable(Routes.ISSUE, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                IssueDetailScreen(onBack = { nav.popBackStack() }, onNeedsSignIn = { nav.navigate(Routes.AUTH) })
            }
            composable(Routes.AUTH) {
                AuthScreen(onClose = { nav.popBackStack() }, onSignedIn = { nav.popBackStack() })
            }
        }
    }
}

/** Standard bottom-nav hop: one copy of each tab, state kept when switching back. */
private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(nav: NavHostController, route: String) {
    NavigationBar(containerColor = CivicColors.Surface, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            val selected = route.substringBefore('?').substringBefore('/') == tab.base
            if (tab.route == Routes.REPORT) {
                // Report is an action, not a place: a green button rather than a tab.
                NavigationBarItem(
                    selected = false,
                    onClick = { nav.navigate(Routes.REPORT) },
                    icon = {
                        Box(Modifier.size(width = 52.dp, height = 36.dp).background(CivicColors.Civic, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(tab.icon, contentDescription = "Report an issue", tint = Color.White)
                        }
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, unselectedTextColor = CivicColors.CivicDeep)
                )
            } else {
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) nav.switchTab(if (tab.route == Routes.LEADERS) Routes.leaders() else tab.route) },
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (tab.civic) CivicColors.CivicDeep else CivicColors.Navy,
                        selectedTextColor = if (tab.civic) CivicColors.CivicDeep else CivicColors.Navy,
                        indicatorColor = if (tab.civic) CivicColors.CivicContainer else MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = CivicColors.TextSecondary,
                        unselectedTextColor = CivicColors.TextSecondary
                    )
                )
            }
        }
    }
}
