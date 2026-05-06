package org.awesoma.trumpinvestitions.ui.screens.portfolio

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.awesoma.trumpinvestitions.data.model.Order
import org.awesoma.trumpinvestitions.data.model.OrderStatus
import org.awesoma.trumpinvestitions.data.model.OrderType
import org.awesoma.trumpinvestitions.data.model.Position
import org.awesoma.trumpinvestitions.ui.viewmodel.PortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen() {
    val vm: PortfolioViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val depositError by vm.depositError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(depositError) {
        if (depositError != null) {
            snackbarHostState.showSnackbar("Ошибка: $depositError")
            vm.clearDepositError()
        }
    }

    var selectedTab       by remember { mutableStateOf(0) }
    val tabs = listOf("Позиции", "История заявок")
    var showDepositDialog  by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    if (showDepositDialog) {
        AmountDialog(
            title     = "Пополнить баланс",
            confirm   = "Пополнить",
            onConfirm = { amount -> vm.deposit(amount); showDepositDialog = false },
            onDismiss = { showDepositDialog = false }
        )
    }
    if (showWithdrawDialog) {
        AmountDialog(
            title     = "Вывести средства",
            confirm   = "Вывести",
            onConfirm = { amount -> vm.withdraw(amount); showWithdrawDialog = false },
            onDismiss = { showWithdrawDialog = false }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Портфель") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val positions = state?.positions ?: emptyList()
            val orders = state?.orders ?: emptyList()
            val cashBalance = state?.cashBalance ?: 0.0
            val totalPnl = state?.totalPnl ?: 0.0

            LazyColumn(contentPadding = padding) {
                item {
                    PortfolioSummaryCard(
                        balance        = cashBalance,
                        totalPnl       = totalPnl,
                        onDepositClick  = { showDepositDialog = true },
                        onWithdrawClick = { showWithdrawDialog = true }
                    )
                }
                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { i, title ->
                            Tab(
                                selected = selectedTab == i,
                                onClick = { selectedTab = i },
                                text = { Text(title) }
                            )
                        }
                    }
                }
                if (selectedTab == 0) {
                    if (positions.isEmpty()) {
                        item { EmptyState("Нет открытых позиций") }
                    } else {
                        items(positions) { position ->
                            PositionItem(position)
                            HorizontalDivider()
                        }
                    }
                } else {
                    if (orders.isEmpty()) {
                        item { EmptyState("История заявок пуста") }
                    } else {
                        items(orders) { order ->
                            OrderItem(order)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountDialog(
    title: String,
    confirm: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value         = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label         = { Text("Сумма") },
                placeholder   = { Text("10000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (amount != null && amount > 0) onConfirm(amount) },
                enabled = amount != null && amount > 0
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun PortfolioSummaryCard(
    balance: Double,
    totalPnl: Double,
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Баланс", style = MaterialTheme.typography.labelMedium)
                    Text("$${String.format("%.2f", balance)}", style = MaterialTheme.typography.headlineSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Общий PnL", style = MaterialTheme.typography.labelMedium)
                    val pnlColor = if (totalPnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    val sign = if (totalPnl >= 0) "+" else ""
                    Text(
                        "$sign$${String.format("%.2f", totalPnl)}",
                        color = pnlColor,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = onDepositClick,
                    modifier = Modifier.weight(1f)
                ) { Text("+ Пополнить") }
                OutlinedButton(
                    onClick  = onWithdrawClick,
                    modifier = Modifier.weight(1f)
                ) { Text("− Вывести") }
            }
        }
    }
}

@Composable
private fun PositionItem(position: Position) {
    ListItem(
        headlineContent = { Text(position.symbol, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text("${position.quantity} акций · ср. $${String.format("%.2f", position.avgBuyPrice)}")
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val pnlColor = if (position.pnl >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                val sign = if (position.pnl >= 0) "+" else ""
                Text("$sign$${String.format("%.2f", position.pnl)}", color = pnlColor)
                Text(
                    "${sign}${String.format("%.2f", position.pnlPercent)}%",
                    color = pnlColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
private fun OrderItem(order: Order) {
    val typeColor = if (order.type == OrderType.BUY) Color(0xFF4CAF50) else Color(0xFFF44336)
    val typeLabel = if (order.type == OrderType.BUY) "Покупка" else "Продажа"
    val statusLabel = when (order.status) {
        OrderStatus.NEW -> "Новая"
        OrderStatus.FILLED -> "Исполнена"
        OrderStatus.CANCELLED -> "Отменена"
        OrderStatus.REJECTED -> "Отклонена"
    }

    ListItem(
        headlineContent = {
            Text("${order.symbol} — $typeLabel ${order.quantity} шт.", color = typeColor)
        },
        supportingContent = {
            Text("${order.createdAt} · $${String.format("%.2f", order.price)}")
        },
        trailingContent = {
            Text(statusLabel, style = MaterialTheme.typography.bodySmall)
        }
    )
}

@Composable
private fun EmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
