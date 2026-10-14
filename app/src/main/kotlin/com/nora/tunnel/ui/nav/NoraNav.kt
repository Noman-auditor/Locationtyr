package com.nora.tunnel.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.nora.tunnel.MainViewModel
import com.nora.tunnel.core.AppLog
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nora.tunnel.data.SettingsStore
import com.nora.tunnel.ui.screens.*
import com.nora.tunnel.ui.theme.NoraColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("profiles", "Profiles", Icons.Filled.Person),
    Tab("routing", "Routing", Icons.Filled.Share),
    Tab("lab", "Lab", Icons.Filled.Build),
    Tab("more", "More", Icons.Filled.MoreHoriz),
)

private fun NavController.goTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoraNav(settings: SettingsStore) {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    val profiles by vm.profiles.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val logs by AppLog.entries.collectAsState()
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in tabs.map { it.route }

    Scaffold(
        containerColor = NoraColors.Bg,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = NoraColors.Card) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.goTab(t.route) },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NoraColors.Mint,
                            selectedTextColor = NoraColors.Mint,
                            indicatorColor = NoraColors.Border,
                            unselectedIconColor = NoraColors.TextDim,
                            unselectedTextColor = NoraColors.TextDim,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "splash", modifier = Modifier.padding(pad)) {
            composable("splash") {
                SplashScreen {
                    scope.launch {
                        val done = settings.onboardingDone.first()
                        nav.navigate(if (done) "home" else "onboarding") { popUpTo("splash") { inclusive = true } }
                    }
                }
            }
            composable("onboarding") {
                OnboardingScreen {
                    scope.launch {
                        settings.setOnboardingDone(true)
                        nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                    }
                }
            }
            composable("home") { HomeScreen(profileName = profiles.firstOrNull { it.id == selectedId }?.name, recent = logs, onServers = { nav.navigate("servers") }, onSettings = { nav.navigate("settings") }) }
            composable("more") { MoreScreen { nav.navigate(it) } }
            composable("profiles") {
                ProfilesScreen(vm, onAdd = { nav.navigate("import") }, onOpen = { nav.navigate("profile_details/$it") })
            }
            composable(
                "profile_details/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                ProfileDetailsScreen(
                    vm, e.arguments?.getLong("id") ?: -1L,
                    onBack = { nav.popBackStack() },
                    onConnect = { nav.goTab("home") },
                )
            }
            composable("servers") { ServersScreen(vm) { nav.popBackStack() } }
            composable("import") { ImportScreen(vm, onManual = { nav.navigate("manual") }) { nav.popBackStack() } }
            composable("manual") { ManualProfileScreen(vm) { nav.popBackStack() } }
            composable("routing") { RoutingScreen(vm) }
            composable("lab") { LabScreen() }
            composable("logs") { LogsScreen { nav.popBackStack() } }
            composable("security") { SecurityScreen(vm, selectedProfile = profiles.firstOrNull { it.id == selectedId }) { nav.popBackStack() } }
            composable("stats") { StatsScreen(vm) { nav.popBackStack() } }
            composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
            composable("about") { AboutScreen { nav.popBackStack() } }
        }
    }
}
