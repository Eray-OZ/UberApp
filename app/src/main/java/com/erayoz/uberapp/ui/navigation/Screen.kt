package com.erayoz.uberapp.ui.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object RoleSelection : Screen("role_selection")
    data object PassengerMap : Screen("passenger_map")
    data object DriverMap : Screen("driver_map")
}
