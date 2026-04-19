package org.awesoma.trumpinvestitions.ui.screens.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import org.awesoma.trumpinvestitions.data.model.Order
import org.awesoma.trumpinvestitions.data.model.OrderStatus
import org.awesoma.trumpinvestitions.data.model.OrderType
import org.awesoma.trumpinvestitions.data.model.Position
import org.awesoma.trumpinvestitions.data.stub.StubRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen() {
    val positions = StubRepository.positions
    val orders = StubRepository.orders
    val user = StubRepository.currentUser
    val totalPnl = positions.sumOf { it.pnl }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Позиции", "История заявок")

    Scaffold(
        topBar = { TopAppBar(title = { Text("Портфель") }) }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                PortfolioSummaryCard(balance = user.balance, totalPnl = totalPnl)
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

@Composable
private fun PortfolioSummaryCard(balance: Double, totalPnl: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
        OrderStatus.ACCEPTED -> "Принята"
        OrderStatus.FILLED -> "Исполнена"
        OrderStatus.CANCELLED -> "Отменена"
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
