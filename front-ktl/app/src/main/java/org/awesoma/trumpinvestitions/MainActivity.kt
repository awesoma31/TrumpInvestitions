package org.awesoma.trumpinvestitions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.awesoma.trumpinvestitions.navigation.Screen
import org.awesoma.trumpinvestitions.ui.screens.auth.LoginScreen
import org.awesoma.trumpinvestitions.ui.screens.auth.RegisterScreen
import org.awesoma.trumpinvestitions.ui.screens.market.StockDetailScreen
import org.awesoma.trumpinvestitions.ui.screens.market.StocksScreen
import org.awesoma.trumpinvestitions.ui.screens.portfolio.PortfolioScreen
import org.awesoma.trumpinvestitions.ui.screens.profile.ProfileScreen
import org.awesoma.trumpinvestitions.ui.theme.TrumpInvestitionsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrumpInvestitionsTheme {
                TrumpInvestitionsApp()
            }
        }
    }
}

private data class BottomNavItem(val route: String, val label: String, val iconRes: Int)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.StockList.route, "Рынок", R.drawable.ic_home),
    BottomNavItem(Screen.Portfolio.route, "Портфель", R.drawable.ic_favorite),
    BottomNavItem(Screen.Profile.route, "Профиль", R.drawable.ic_account_box),
)

@Composable
fun TrumpInvestitionsApp() {
    val context = LocalContext.current
    val app = context.applicationContext as TrumpApp
    val isLoggedIn by app.tokenManager.isLoggedInFlow().collectAsState(initial = false)

    if (!isLoggedIn) {
        AuthFlow()
    } else {
        MainApp()
    }
}

@Composable
fun AuthFlow() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = { /* tokenManager flow handles transition */ },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = { /* tokenManager flow handles transition */ },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                this.item(
                    icon = { Icon(painterResource(item.iconRes), contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = isSelected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) {
        MainNavHost(navController = navController)
    }
}

@Composable
fun MainNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.StockList.route) {
        composable(Screen.StockList.route) {
            StocksScreen(onStockClick = { symbol ->
                navController.navigate(Screen.StockDetail.createRoute(symbol))
            })
        }
        composable(
            route = Screen.StockDetail.route,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: return@composable
            StockDetailScreen(symbol = symbol, onBack = { navController.popBackStack() })
        }
        composable(Screen.Portfolio.route) {
            PortfolioScreen()
        }
        composable(Screen.Profile.route) {
            ProfileScreen(onLogout = { /* tokenManager flow handles transition */ })
        }
    }
}