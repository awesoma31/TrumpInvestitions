package org.awesoma.trumpinvestitions.ui.screens.market

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.awesoma.trumpinvestitions.data.model.PricePoint
import org.awesoma.trumpinvestitions.data.network.dto.OrderBookResponseDto
import org.awesoma.trumpinvestitions.ui.viewmodel.OrderEvent
import org.awesoma.trumpinvestitions.ui.viewmodel.StockDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(symbol: String, onBack: () -> Unit) {
    val vm: StockDetailViewModel = viewModel(factory = StockDetailViewModel.factory(symbol))
    val stock by vm.stock.collectAsState()
    val candles by vm.candles.collectAsState()
    val orderBook by vm.orderBook.collectAsState()
    val orderEvent by vm.orderEvent.collectAsState()

    var showOrderDialog by remember { mutableStateOf(false) }
    var orderType by remember { mutableStateOf("BUY") }
    var isOrderError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orderEvent) {
        if (orderEvent != null) {
            isOrderError = orderEvent is OrderEvent.Error
            val message = when (val ev = orderEvent!!) {
                is OrderEvent.Success -> ev.message
                is OrderEvent.Error   -> ev.message
            }
            snackbarHostState.showSnackbar(message)
            vm.clearOrderEvent()
        }
    }

    if (showOrderDialog) {
        OrderDialog(
            symbol = symbol,
            type = orderType,
            onDismiss = { showOrderDialog = false },
            onConfirm = { quantity ->
                vm.placeOrder(orderType, quantity)
                showOrderDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isOrderError) Color(0xFFC62828) else Color(0xFF2E7D32),
                    contentColor = Color.White,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stock?.symbol ?: symbol) },
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
        if (stock == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = padding) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stock!!.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$${String.format("%.2f", stock!!.price)}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        val changeColor =
                            if (stock!!.changePercent >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        val sign = if (stock!!.changePercent >= 0) "+" else ""
                        Text(
                            "$sign${String.format("%.2f", stock!!.changePercent)}%",
                            color = changeColor
                        )
                    }
                }
                item { PriceChart(points = candles) }
                item {
                    Text(
                        "Биржевой стакан",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    if (orderBook != null) {
                        RealOrderBook(orderBook = orderBook!!)
                    } else {
                        OrderBookStub(
                            highestBid = stock!!.highestBid,
                            lowestAsk = stock!!.lowestAsk
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun PriceChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("Загрузка графика...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val minPrice = points.minOf { it.price }
    val maxPrice = points.maxOf { it.price }
    val priceRange = (maxPrice - minPrice).coerceAtLeast(0.01)
    val isUp = points.last().price >= points.first().price
    val lineColor = if (isUp) Color(0xFF4CAF50) else Color(0xFFF44336)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = i * stepX
            val y = (h * (1f - ((pt.price - minPrice) / priceRange).toFloat())).coerceIn(0f, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun RealOrderBook(orderBook: OrderBookResponseDto) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bid", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelLarge)
            Text("Ask", color = Color(0xFFF44336), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(4.dp))
        val maxRows = maxOf(orderBook.bids.size, orderBook.asks.size)
        repeat(maxRows) { i ->
            val bid = orderBook.bids.getOrNull(i)
            val ask = orderBook.asks.getOrNull(i)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (bid != null) "${bid.price} (${bid.quantity})" else "—",
                    color = Color(0xFF4CAF50)
                )
                Text(
                    if (ask != null) "${ask.price} (${ask.quantity})" else "—",
                    color = Color(0xFFF44336)
                )
            }
        }
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
fun OrderDialog(
    symbol: String,
    type: String,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int) -> Unit
) {
    var quantityText by remember { mutableStateOf("1") }
    val title = if (type == "BUY") "Купить $symbol" else "Продать $symbol"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Количество акций:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                    label = { Text("Количество") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val qty = quantityText.toIntOrNull()?.takeIf { it > 0 } ?: 1
                onConfirm(qty)
            }) { Text("Подтвердить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
