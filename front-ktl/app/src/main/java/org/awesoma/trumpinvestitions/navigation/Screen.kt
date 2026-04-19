package org.awesoma.trumpinvestitions.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object StockList : Screen("stock_list")
    object StockDetail : Screen("stock_detail/{symbol}") {
        fun createRoute(symbol: String) = "stock_detail/$symbol"
    }
    object Portfolio : Screen("portfolio")
    object Profile : Screen("profile")
}
