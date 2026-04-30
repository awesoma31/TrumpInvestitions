package com.trumpinvestitions.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trumpinvestitions.trading.presentation.ui.screen.TradingScreen

@Composable
fun TrumpInvestitionsNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Trading.route
    ) {
        composable(Screen.Trading.route) {
            TradingScreen()
        }
        
        // Другие экраны будут добавлены здесь
        // composable(Screen.Portfolio.route) { PortfolioScreen() }
        // composable(Screen.Charts.route) { ChartsScreen() }
        // composable(Screen.Settings.route) { SettingsScreen() }
        // composable(Screen.Auth.route) { AuthScreen() }
    }
}

sealed class Screen(val route: String) {
    object Trading : Screen("trading")
    object Portfolio : Screen("portfolio")
    object Charts : Screen("charts")
    object Settings : Screen("settings")
    object Auth : Screen("auth")
}