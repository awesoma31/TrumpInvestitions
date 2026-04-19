package org.awesoma.trumpinvestitions.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.awesoma.trumpinvestitions.data.stub.StubRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(symbol: String, onBack: () -> Unit) {
    val stock = StubRepository.getStock(symbol)
    var showOrderDialog by remember { mutableStateOf(false) }
    var orderType by remember { mutableStateOf("BUY") }

    if (stock == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Акция не найдена")
        }
        return
    }

    if (showOrderDialog) {
        OrderDialog(
            symbol = symbol,
            type = orderType,
            onDismiss = { showOrderDialog = false },
            onConfirm = { showOrderDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stock.symbol) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { orderType = "BUY"; showOrderDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("Купить") }
                Button(
                    onClick = { orderType = "SELL"; showOrderDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Продать") }
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stock.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("$${String.format("%.2f", stock.price)}", style = MaterialTheme.typography.headlineMedium)
                    val changeColor = if (stock.changePercent >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    val sign = if (stock.changePercent >= 0) "+" else ""
                    Text("$sign${String.format("%.2f", stock.changePercent)}%", color = changeColor)
                }
            }
            item { ChartPlaceholder() }
            item {
                Text(
                    "Биржевой стакан",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item { OrderBookStub(highestBid = stock.highestBid, lowestAsk = stock.lowestAsk) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun ChartPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Text("График цены (TODO)", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OrderBookStub(highestBid: Double, lowestAsk: Double) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bid", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelLarge)
            Text("Ask", color = Color(0xFFF44336), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        repeat(5) { i ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(String.format("%.2f", highestBid - i * 0.01), color = Color(0xFF4CAF50))
                Text(String.format("%.2f", lowestAsk + i * 0.01), color = Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun OrderDialog(symbol: String, type: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var quantity by remember { mutableStateOf("1") }
    val title = if (type == "BUY") "Купить $symbol" else "Продать $symbol"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Количество акций:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                    label = { Text("Количество") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Подтвердить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
