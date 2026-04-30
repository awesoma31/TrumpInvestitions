package com.trumpinvestitions.trading.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trumpinvestitions.trading.domain.model.Order
import com.trumpinvestitions.trading.domain.model.OrderSide
import com.trumpinvestitions.trading.domain.model.OrderType
import com.trumpinvestitions.trading.presentation.viewmodel.TradingViewModel
import java.math.BigDecimal
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingScreen(
    viewModel: TradingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            // Показать снэкбар с ошибкой
        }
    }

    LaunchedEffect(uiState.orderCreated) {
        if (uiState.orderCreated) {
            // Показать сообщение об успешном создании ордера
            viewModel.orderCreatedHandled()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Торговля",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            QuotesList(quotes = uiState.quotes)
        }

        Spacer(modifier = Modifier.height(24.dp))

        OrderCreationSection(
            isCreatingOrder = uiState.isCreatingOrder,
            onCreateOrder = { order ->
                viewModel.createOrder(order)
            }
        )
    }
}

@Composable
fun QuotesList(quotes: List<com.trumpinvestitions.trading.domain.model.Quote>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Котировки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(quotes) { quote ->
                    QuoteItem(quote = quote)
                }
            }
        }
    }
}

@Composable
fun QuoteItem(quote: com.trumpinvestitions.trading.domain.model.Quote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = quote.ticker,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Объем: ${quote.volume}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$${quote.price}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            val changeColor = if (quote.change.signum() >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Text(
                text = "${if (quote.change.signum() >= 0) "+" else ""}${quote.change} (${quote.changePercent}%)",
                style = MaterialTheme.typography.bodySmall,
                color = changeColor
            )
        }
    }
    
    Divider()
}

@Composable
fun OrderCreationSection(
    isCreatingOrder: Boolean,
    onCreateOrder: (Order) -> Unit
) {
    var ticker by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var orderSide by remember { mutableStateOf(OrderSide.BUY) }
    var orderType by remember { mutableStateOf(OrderType.LIMIT) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Создание заявки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = ticker,
                onValueChange = { ticker = it.uppercase() },
                label = { Text("Тикер") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Количество") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = price,
                onValueChange = { price = it },
                label = { Text("Цена") },
                modifier = Modifier.fillMaxWidth(),
                enabled = orderType == OrderType.LIMIT
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Выбор типа ордера
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = when (orderType) {
                        OrderType.MARKET -> "Рыночный"
                        OrderType.LIMIT -> "Лимитный"
                    },
                    onValueChange = { },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    OrderType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(
                                when (type) {
                                    OrderType.MARKET -> "Рыночный"
                                    OrderType.LIMIT -> "Лимитный"
                                }
                            ) },
                            onClick = {
                                orderType = type
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Выбор стороны ордера
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { orderSide = OrderSide.BUY },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orderSide == OrderSide.BUY) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Text(
                        text = "Купить",
                        color = if (orderSide == OrderSide.BUY) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
                
                Button(
                    onClick = { orderSide = OrderSide.SELL },
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orderSide == OrderSide.SELL) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Text(
                        text = "Продать",
                        color = if (orderSide == OrderSide.SELL) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val order = Order(
                        id = "", // Будет сгенерирован на сервере
                        ticker = ticker,
                        type = orderType,
                        side = orderSide,
                        quantity = quantity.toIntOrNull() ?: 0,
                        price = if (orderType == OrderType.MARKET) {
                            BigDecimal.ZERO
                        } else {
                            BigDecimal(price.ifEmpty { "0" })
                        },
                        status = com.trumpinvestitions.trading.domain.model.OrderStatus.PENDING,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                    onCreateOrder(order)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = ticker.isNotBlank() && 
                         quantity.isNotBlank() && 
                         (orderType == OrderType.MARKET || price.isNotBlank()) &&
                         !isCreatingOrder
            ) {
                if (isCreatingOrder) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = when (orderSide) {
                            OrderSide.BUY -> "Купить"
                            OrderSide.SELL -> "Продать"
                        }
                    )
                }
            }
        }
    }
}