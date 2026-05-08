package com.lifemanga.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifemanga.android.ui.create.CreateScreen
import com.lifemanga.android.ui.detail.DetailScreen
import com.lifemanga.android.ui.history.HistoryScreen
import com.lifemanga.android.ui.settings.SettingsScreen

object Routes {
    const val CREATE = "create"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{itemId}"
    fun detail(id: String) = "detail/$id"
}

@Composable
fun LifeMangaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CREATE) {
        composable(Routes.CREATE) {
            CreateScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("itemId").orEmpty()
            DetailScreen(itemId = id, onBack = { navController.popBackStack() })
        }
    }
}
