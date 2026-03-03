package com.euhomy.fridge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.euhomy.fridge.data.CredentialsStore
import com.euhomy.fridge.data.DefaultCredentials
import com.euhomy.fridge.ui.screen.FridgeScreen
import com.euhomy.fridge.ui.screen.ScanScreen
import com.euhomy.fridge.ui.screen.SetupScreen

private const val ROUTE_FRIDGE = "fridge"
private const val ROUTE_SCAN   = "scan"
private const val ROUTE_SETUP  = "setup?mac={mac}"

@Composable
fun EuhomyNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val hasCredentials = remember {
        CredentialsStore(context).hasCredentials() || DefaultCredentials.preset != null
    }

    NavHost(
        navController = navController,
        startDestination = if (hasCredentials) ROUTE_FRIDGE else ROUTE_SCAN,
    ) {

        // Main fridge control screen
        composable(ROUTE_FRIDGE) {
            FridgeScreen(
                onOpenSettings = {
                    navController.navigate(ROUTE_SCAN)
                }
            )
        }

        // BLE scanner — select device to configure
        composable(ROUTE_SCAN) {
            ScanScreen(
                onDeviceSelected = { device ->
                    navController.navigate("setup?mac=${device.address}")
                }
            )
        }

        // Setup / credentials form
        composable(
            route     = "setup?mac={mac}",
            arguments = listOf(navArgument("mac") {
                type          = NavType.StringType
                defaultValue  = ""
                nullable      = false
            }),
        ) { backStack ->
            val mac = backStack.arguments?.getString("mac") ?: ""
            SetupScreen(
                prefillMac = mac.ifBlank { null },
                onComplete = {
                    navController.navigate(ROUTE_FRIDGE) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
