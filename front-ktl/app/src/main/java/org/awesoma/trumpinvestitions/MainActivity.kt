package org.awesoma.trumpinvestitions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }

    if (!isLoggedIn) {
        AuthFlow(onLoginSuccess = { isLoggedIn = true })
    } else {
        MainApp(onLogout = { isLoggedIn = false })
    }
}

@Composable
fun AuthFlow(onLoginSuccess: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = onLoginSuccess,
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainApp(onLogout: () -> Unit) {
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
        MainNavHost(navController = navController, onLogout = onLogout)
    }
}

@Composable
fun MainNavHost(navController: NavHostController, onLogout: () -> Unit) {
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
            ProfileScreen(onLogout = onLogout)
        }
    }
}
