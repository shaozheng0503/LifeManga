package com.lifemanga.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifemanga.android.ui.characters.CharacterCreateScreen
import com.lifemanga.android.ui.characters.CharacterDetailScreen
import com.lifemanga.android.ui.characters.CharacterLibraryScreen
import com.lifemanga.android.ui.create.CreateScreen
import com.lifemanga.android.ui.detail.DetailScreen
import com.lifemanga.android.ui.history.HistoryScreen
import com.lifemanga.android.ui.projects.ProjectListScreen
import com.lifemanga.android.ui.settings.SettingsScreen

object Routes {
    // Bottom nav tabs
    const val PROJECTS = "projects"
    const val CHARACTERS_ROOT = "characters_root"
    const val SETTINGS = "settings"

    // Detail screens
    const val PROJECT_DETAIL = "project/{projectId}"
    const val HISTORY = "history"
    const val DETAIL = "detail/{itemId}"
    const val CHARACTER_DETAIL = "character/{characterId}"
    const val CHARACTER_CREATE = "character_create"

    fun projectDetail(id: String) = "project/$id"
    fun detail(id: String) = "detail/$id"
    fun characterDetail(id: String) = "character/$id"
}

private sealed class BottomTab(val route: String, val label: String) {
    object Projects : BottomTab(Routes.PROJECTS, "工程")
    object Characters : BottomTab(Routes.CHARACTERS_ROOT, "角色库")
    object Settings : BottomTab(Routes.SETTINGS, "设置")
}

private val bottomTabs = listOf(
    BottomTab.Projects,
    BottomTab.Characters,
    BottomTab.Settings,
)

/** Routes that show the bottom navigation bar */
private val bottomNavRoutes = setOf(Routes.PROJECTS, Routes.CHARACTERS_ROOT, Routes.SETTINGS)

@Composable
fun LifeMangaNavHost() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route

    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                when (tab) {
                                    is BottomTab.Projects -> Icon(Icons.Default.AutoStories, contentDescription = tab.label)
                                    is BottomTab.Characters -> Icon(Icons.Default.Face, contentDescription = tab.label)
                                    is BottomTab.Settings -> Icon(Icons.Default.Settings, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = navController, startDestination = Routes.PROJECTS) {
                // Bottom nav roots
                composable(Routes.PROJECTS) {
                    ProjectListScreen(
                        onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.CHARACTERS_ROOT) {
                    CharacterLibraryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCharacter = { id -> navController.navigate(Routes.characterDetail(id)) },
                        onCreateCharacter = { navController.navigate(Routes.CHARACTER_CREATE) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }

                // Project detail (create + history scoped to project)
                composable(
                    route = Routes.PROJECT_DETAIL,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
                ) { entry ->
                    val projectId = entry.arguments?.getString("projectId").orEmpty()
                    // Project detail reuses CreateScreen with history nav
                    CreateScreen(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }

                // History + detail (shared across projects for now)
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenItem = { id -> navController.navigate(Routes.detail(id)) },
                    )
                }
                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("itemId").orEmpty()
                    DetailScreen(itemId = id, onBack = { navController.popBackStack() })
                }

                // Character detail
                composable(
                    route = Routes.CHARACTER_DETAIL,
                    arguments = listOf(navArgument("characterId") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("characterId").orEmpty()
                    CharacterDetailScreen(
                        characterId = id,
                        onBack = { navController.popBackStack() },
                    )
                }

                // Character create
                composable(Routes.CHARACTER_CREATE) {
                    CharacterCreateScreen(
                        onBack = { navController.popBackStack() },
                        onCreated = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
