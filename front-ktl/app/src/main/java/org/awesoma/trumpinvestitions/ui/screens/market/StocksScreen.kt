package org.awesoma.trumpinvestitions.ui.screens.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import org.awesoma.trumpinvestitions.data.model.Stock
import org.awesoma.trumpinvestitions.ui.viewmodel.StocksViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksScreen(onStockClick: (String) -> Unit) {
    val vm: StocksViewModel = viewModel()
    val stocks by vm.stocks.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Рынок") }) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(stocks) { stock ->
                    StockListItem(stock = stock, onClick = { onStockClick(stock.symbol) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun StockListItem(stock: Stock, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(stock.symbol, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(stock.name) },
        trailingContent = {
            Column(
                modifier = Modifier.fillMaxWidth(0.3f),
                horizontalAlignment = Alignment.End
            ) {
                Text("$${String.format("%.2f", stock.price)}", style = MaterialTheme.typography.bodyLarge)
                val changeColor = if (stock.changePercent >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                val sign = if (stock.changePercent >= 0) "+" else ""
                Text(
                    "$sign${String.format("%.2f", stock.changePercent)}%",
                    color = changeColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
