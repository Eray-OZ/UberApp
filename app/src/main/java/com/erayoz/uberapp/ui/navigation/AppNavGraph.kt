package com.erayoz.uberapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.erayoz.uberapp.ui.auth.AuthScreen
import com.erayoz.uberapp.ui.driver.DriverMapScreen
import com.erayoz.uberapp.ui.passenger.PassengerMapScreen
import com.erayoz.uberapp.ui.role.RoleSelectionScreen

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Auth.route,
        modifier = modifier
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = { role ->
                    if (role != null) {
                        val destination = if (role == "passenger") {
                            Screen.PassengerMap.route
                        } else {
                            Screen.DriverMap.route
                        }
                        navController.navigate(destination) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.RoleSelection.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(
                onPassengerSelected = {
                    navController.navigate(Screen.PassengerMap.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                },
                onDriverSelected = {
                    navController.navigate(Screen.DriverMap.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.PassengerMap.route) {
            PassengerMapScreen(
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.DriverMap.route) {
            DriverMapScreen(
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
