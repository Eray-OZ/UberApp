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
            AuthScreen()
        }
        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen()
        }
        composable(Screen.PassengerMap.route) {
            PassengerMapScreen()
        }
        composable(Screen.DriverMap.route) {
            DriverMapScreen()
        }
    }
}
