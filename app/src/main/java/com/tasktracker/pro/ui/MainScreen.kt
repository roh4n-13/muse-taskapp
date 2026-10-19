package com.tasktracker.pro.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tasktracker.pro.ui.navigation.Routes
import com.tasktracker.pro.ui.screens.CompletedScreen
import com.tasktracker.pro.ui.screens.DashboardScreen
import com.tasktracker.pro.ui.screens.ReportScreen
import com.tasktracker.pro.ui.screens.SettingsScreen
import com.tasktracker.pro.ui.screens.SheetsScreen
import com.tasktracker.pro.ui.screens.TaskDetailScreen
import com.tasktracker.pro.ui.screens.TaskEditScreen
import com.tasktracker.pro.ui.screens.TaskListScreen

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

private val bottomDests = listOf(
    BottomDest(Routes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    BottomDest(Routes.TASKS, "Tasks", Icons.Filled.List),
    BottomDest(Routes.COMPLETED, "Completed", Icons.Filled.CheckCircle),
    BottomDest(Routes.REPORT, "Report", Icons.Filled.Description),
    BottomDest(Routes.SHEETS, "Sheets", Icons.Filled.Cloud),
    BottomDest(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

/** App shell: bottom navigation + nav graph for the six tabs and task screens. */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = bottomDests.any { it.route == currentRoute }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val currentDestination = navBackStackEntry?.destination
                    bottomDests.forEach { dest ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen(navController, snackbarHostState) }
            composable(Routes.TASKS) { TaskListScreen(navController, snackbarHostState) }
            composable(Routes.COMPLETED) { CompletedScreen(navController, snackbarHostState) }
            composable(Routes.REPORT) { ReportScreen() }
            composable(Routes.SHEETS) { SheetsScreen(snackbarHostState) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                Routes.TASK_DETAIL,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { entry ->
                TaskDetailScreen(
                    taskId = entry.arguments?.getString("taskId").orEmpty(),
                    navController = navController,
                    snackbarHostState = snackbarHostState
                )
            }
            composable(
                Routes.TASK_EDIT,
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                TaskEditScreen(
                    taskId = entry.arguments?.getString("taskId"),
                    navController = navController,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }
}
