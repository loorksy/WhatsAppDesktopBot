package com.whatsappdesktopbot.local.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.whatsappdesktopbot.local.ui.screens.BulkSendScreen
import com.whatsappdesktopbot.local.ui.screens.ClientsScreen
import com.whatsappdesktopbot.local.ui.screens.ForwardingScreen
import com.whatsappdesktopbot.local.ui.screens.GroupsScreen
import com.whatsappdesktopbot.local.ui.screens.LinkScreen
import com.whatsappdesktopbot.local.ui.screens.LogsScreen
import com.whatsappdesktopbot.local.ui.screens.SettingsScreen
import com.whatsappdesktopbot.local.ui.screens.StatusScreen
import com.whatsappdesktopbot.local.ui.viewmodel.BotViewModel

@Composable
fun AppRoot(viewModel: BotViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    val primaryDestinations = listOf(
        AppDestination.Status,
        AppDestination.Link,
        AppDestination.Groups,
        AppDestination.Clients,
        AppDestination.Forwarding,
        AppDestination.Bulk,
        AppDestination.Logs,
        AppDestination.Settings,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                primaryDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(AppDestination.Status.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Status.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppDestination.Status.route) { StatusScreen(viewModel) }
            composable(AppDestination.Link.route) { LinkScreen(viewModel) }
            composable(AppDestination.Groups.route) { GroupsScreen(viewModel) }
            composable(AppDestination.Clients.route) { ClientsScreen(viewModel) }
            composable(AppDestination.Forwarding.route) { ForwardingScreen(viewModel) }
            composable(AppDestination.Bulk.route) { BulkSendScreen(viewModel) }
            composable(AppDestination.Logs.route) { LogsScreen(viewModel) }
            composable(AppDestination.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}
