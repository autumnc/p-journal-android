package com.pjournal.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.ui.screens.browser.BrowserScreen
import com.pjournal.app.ui.screens.editor.EditorScreen
import com.pjournal.app.ui.screens.home.HomeScreen
import com.pjournal.app.ui.screens.settings.SettingsScreen
import com.pjournal.app.ui.screens.viewer.ViewerScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Editor : Screen("editor/{prompt}?filename={filename}&mode={mode}") {
        fun createRoute(prompt: String = "", filename: String = "", mode: String = "prompt"): String {
            val encodedFn = java.net.URLEncoder.encode(filename, "UTF-8")
            val encodedMode = java.net.URLEncoder.encode(mode, "UTF-8")
            val params = mutableListOf<String>()
            if (filename.isNotBlank()) params.add("filename=$encodedFn")
            params.add("mode=$encodedMode")
            return "editor/$prompt?${params.joinToString("&")}"
        }
    }
    object Browser : Screen("browser")
    object Viewer : Screen("viewer/{filename}") {
        fun createRoute(filename: String) = "viewer/$filename"
    }
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    darkTheme: Boolean = false,
    einkMode: Boolean = false,
    onToggleTheme: () -> Unit = {},
    prefs: PreferencesManager? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefsManager = prefs ?: PreferencesManager(context)
    val focusMode by prefsManager.focusMode.collectAsState(initial = false)
    val encryptionEnabled by prefsManager.encryptionEnabled.collectAsState(initial = false)
    val encryptionPassword by prefsManager.encryptionPassword.collectAsState(initial = "")

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                focusMode = focusMode,
                isDarkTheme = darkTheme,
                einkMode = einkMode,
                encryptionEnabled = encryptionEnabled,
                encryptionPassword = encryptionPassword,
                onToggleTheme = onToggleTheme,
                onPromptWrite = { prompt ->
                    navController.navigate(Screen.Editor.createRoute(prompt ?: "", mode = "prompt"))
                },
                onFreeWrite = {
                    navController.navigate(Screen.Editor.createRoute(mode = "free"))
                },
                onBrowse = {
                    navController.navigate(Screen.Browser.route)
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("prompt") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("filename") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "prompt"
                }
            )
        ) { backStackEntry ->
            val prompt = backStackEntry.arguments?.getString("prompt") ?: ""
            val filename = backStackEntry.arguments?.getString("filename") ?: ""
            val mode = backStackEntry.arguments?.getString("mode") ?: "prompt"
            EditorScreen(
                promptText = prompt.ifBlank { null },
                editFilename = filename.ifBlank { null },
                isPromptMode = mode == "prompt",
                focusMode = focusMode,
                onDone = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Browser.route) {
            BrowserScreen(
                onViewEntry = { filename ->
                    navController.navigate(Screen.Viewer.createRoute(filename))
                },
                onEditEntry = { filename ->
                    navController.navigate(Screen.Editor.createRoute(filename = filename))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Viewer.route,
            arguments = listOf(navArgument("filename") { type = NavType.StringType })
        ) { backStackEntry ->
            val filename = backStackEntry.arguments?.getString("filename") ?: ""
            ViewerScreen(
                filename = filename,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
