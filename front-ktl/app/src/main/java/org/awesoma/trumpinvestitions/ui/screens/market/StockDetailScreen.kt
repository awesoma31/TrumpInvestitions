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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.awesoma.trumpinvestitions.data.model.Candle
import org.awesoma.trumpinvestitions.data.model.PricePoint
import org.awesoma.trumpinvestitions.data.network.dto.OrderBookResponseDto
import org.awesoma.trumpinvestitions.ui.viewmodel.OrderEvent
import org.awesoma.trumpinvestitions.ui.viewmodel.StockDetailViewModel
import org.awesoma.trumpinvestitions.ui.viewmodel.TimeFrame

// ─────────────────────────────────────────────────────────────────────────────
// Chart type
// ─────────────────────────────────────────────────────────────────────────────

enum class ChartType { CANDLE, LINE }

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(symbol: String, onBack: () -> Unit) {
    val vm: StockDetailViewModel = viewModel(factory = StockDetailViewModel.factory(symbol))
    val stock             by vm.stock.collectAsState()
    val candles           by vm.candles.collectAsState()
    val selectedTimeFrame by vm.selectedTimeFrame.collectAsState()
    val orderBook         by vm.orderBook.collectAsState()
    val orderEvent        by vm.orderEvent.collectAsState()

    var chartType       by remember { mutableStateOf(ChartType.CANDLE) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var orderType       by remember { mutableStateOf("BUY") }
    var isOrderError    by remember { mutableStateOf(false) }

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
            symbol    = symbol,
            type      = orderType,
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
                    snackbarData   = data,
                    containerColor = if (isOrderError) Color(0xFFC62828) else Color(0xFF2E7D32),
                    contentColor   = Color.White,
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
                    onClick  = { orderType = "BUY"; showOrderDialog = true },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("Купить") }
                Button(
                    onClick  = { orderType = "SELL"; showOrderDialog = true },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Продать") }
            }
        }
    ) { padding ->
        if (stock == null) {
            Box(
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(contentPadding = padding) {

                // ── Price header ──────────────────────────────────────────
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stock!!.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$${formatPrice(stock!!.price)}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        val changeColor = if (stock!!.changePercent >= 0) Color(0xFF4CAF50)
                                          else Color(0xFFF44336)
                        val sign = if (stock!!.changePercent >= 0) "+" else ""
                        Text(
                            "$sign${String.format("%.2f", stock!!.changePercent)}%",
                            color = changeColor
                        )
                    }
                }

                // ── Time-frame + chart type selector ─────────────────────
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            TimeFrameSelector(
                                selected = selectedTimeFrame,
                                onSelect = { vm.selectTimeFrame(it) }
                            )
                        }
                        ChartTypeToggle(
                            current  = chartType,
                            onToggle = { chartType = it },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                }

                // ── Chart ─────────────────────────────────────────────────
                item {
                    if (chartType == ChartType.CANDLE) {
                        CandlestickChart(
                            candles  = candles,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        LineChart(
                            points   = candles.map { PricePoint(it.timestamp, it.close) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // ── Order book ────────────────────────────────────────────
                item {
                    Text(
                        "Биржевой стакан",
                        style    = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                item {
                    if (orderBook != null) RealOrderBook(orderBook!!)
                    else OrderBookStub(
                        highestBid = stock!!.highestBid,
                        lowestAsk  = stock!!.lowestAsk
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Time-frame selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TimeFrameSelector(
    selected: TimeFrame,
    onSelect: (TimeFrame) -> Unit
) {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TimeFrame.entries.forEach { tf ->
            val isSelected  = tf == selected
            val bgColor     = if (isSelected) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surfaceVariant
            val textColor   = if (isSelected) MaterialTheme.colorScheme.onPrimary
                              else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                onClick = { onSelect(tf) },
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(6.dp),
                color    = bgColor
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(tf.label, color = textColor, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Candlestick chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CandlestickChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    if (candles.size < 2) {
        Box(
            modifier         = modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            if (candles.isEmpty())
                CircularProgressIndicator()
            else
                Text("Недостаточно данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor      = Color.Gray.copy(alpha = 0.15f)
    val bullColor      = Color(0xFF26A69A)   // teal  — close > open
    val bearColor      = Color(0xFFEF5350)   // red   — close < open

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        val priceAxisWidth = 68.dp.toPx()
        val chartW = size.width - priceAxisWidth
        val chartH = size.height

        // ── price range ───────────────────────────────────────────────────
        val minP = candles.minOf { it.low }
        val maxP = candles.maxOf { it.high }
        val pad  = (maxP - minP) * 0.05       // 5% vertical padding
        val lo   = minP - pad
        val hi   = maxP + pad
        val rng  = (hi - lo).coerceAtLeast(0.0001)

        fun px(price: Double): Float =
            (chartH * (1.0 - (price - lo) / rng)).toFloat().coerceIn(0f, chartH)

        // ── grid + price labels ───────────────────────────────────────────
        val paint = android.graphics.Paint().apply {
            textSize  = 10.sp.toPx()
            color     = labelTextColor
            textAlign = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
        }
        val gridLevels = 5
        repeat(gridLevels) { i ->
            val fraction = i / (gridLevels - 1).toFloat()
            val price    = hi - rng * fraction
            val y        = px(price)

            drawLine(gridColor, Offset(0f, y), Offset(chartW, y), strokeWidth = 0.5.dp.toPx())

            drawIntoCanvas { cv ->
                cv.nativeCanvas.drawText(
                    formatPrice(price),
                    chartW + 4.dp.toPx(),
                    y + paint.textSize / 3f,
                    paint
                )
            }
        }

        // ── candles ───────────────────────────────────────────────────────
        val slotW  = chartW / candles.size
        val bodyW  = (slotW * 0.65f).coerceAtLeast(2.dp.toPx())
        val wickW  = 1.2.dp.toPx()

        candles.forEachIndexed { i, c ->
            val cx    = (i + 0.5f) * slotW
            val color = if (c.close >= c.open) bullColor else bearColor

            val highY  = px(c.high)
            val lowY   = px(c.low)
            val openY  = px(c.open)
            val closeY = px(c.close)

            // upper wick: high → top of body
            val bodyTop    = minOf(openY, closeY)
            val bodyBottom = maxOf(openY, closeY)
            val bodyH      = (bodyBottom - bodyTop).coerceAtLeast(1f)

            // wick (full: high to low)
            drawLine(color, Offset(cx, highY), Offset(cx, lowY), strokeWidth = wickW)

            // body
            drawRect(color, Offset(cx - bodyW / 2f, bodyTop), Size(bodyW, bodyH))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chart type toggle  (🕯 / 📈)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChartTypeToggle(
    current:  ChartType,
    onToggle: (ChartType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ChartType.entries.forEach { type ->
            val label     = if (type == ChartType.CANDLE) "Свечи" else "Линия"
            val isActive  = type == current
            val bgColor   = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
            val txtColor  = if (isActive) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                onClick  = { onToggle(type) },
                shape    = RoundedCornerShape(6.dp),
                color    = bgColor
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(label, color = txtColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Line chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LineChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(
            modifier         = modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            if (points.isEmpty()) CircularProgressIndicator()
            else Text("Недостаточно данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor      = Color.Gray.copy(alpha = 0.15f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        val priceAxisWidth = 68.dp.toPx()
        val chartW = size.width - priceAxisWidth
        val chartH = size.height

        val minP = points.minOf { it.price }
        val maxP = points.maxOf { it.price }
        val pad  = (maxP - minP) * 0.05
        val lo   = minP - pad
        val hi   = maxP + pad
        val rng  = (hi - lo).coerceAtLeast(0.0001)

        fun px(price: Double): Float =
            (chartH * (1.0 - (price - lo) / rng)).toFloat().coerceIn(0f, chartH)

        // Grid + price labels
        val paint = android.graphics.Paint().apply {
            textSize    = 10.sp.toPx()
            color       = labelTextColor
            textAlign   = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
        }
        repeat(5) { i ->
            val fraction = i / 4f
            val price    = hi - rng * fraction
            val y        = px(price)
            drawLine(gridColor, Offset(0f, y), Offset(chartW, y), strokeWidth = 0.5.dp.toPx())
            drawIntoCanvas { cv ->
                cv.nativeCanvas.drawText(
                    formatPrice(price),
                    chartW + 4.dp.toPx(),
                    y + paint.textSize / 3f,
                    paint
                )
            }
        }

        // Line
        val isUp      = points.last().price >= points.first().price
        val lineColor = if (isUp) Color(0xFF26A69A) else Color(0xFFEF5350)
        val stepX     = chartW / (points.size - 1)
        val path      = Path()

        points.forEachIndexed { i, pt ->
            val x = i * stepX
            val y = px(pt.price)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path  = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun formatPrice(price: Double): String = when {
    price >= 10_000 -> String.format("%.0f", price)
    price >= 100    -> String.format("%.2f", price)
    else            -> String.format("%.4f", price)
}

// ─────────────────────────────────────────────────────────────────────────────
// Order book
// ─────────────────────────────────────────────────────────────────────────────

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
                Text(String.format("%.2f", lowestAsk + i * 0.01),  color = Color(0xFFF44336))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Order dialog
// ─────────────────────────────────────────────────────────────────────────────

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
        text  = {
            Column {
                Text("Количество акций:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() } },
                    label         = { Text("Количество") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
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
