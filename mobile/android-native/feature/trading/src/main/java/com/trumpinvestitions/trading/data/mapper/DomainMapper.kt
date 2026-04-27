package com.trumpinvestitions.trading.data.mapper

import com.trumpinvestitions.core.network.dto.OrderDto
import com.trumpinvestitions.core.network.dto.QuoteDto
import com.trumpinvestitions.trading.domain.model.Order
import com.trumpinvestitions.trading.domain.model.OrderSide
import com.trumpinvestitions.trading.domain.model.OrderStatus
import com.trumpinvestitions.trading.domain.model.OrderType
import com.trumpinvestitions.trading.domain.model.Quote
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun QuoteDto.toDomain(): Quote {
    return Quote(
        ticker = ticker,
        price = BigDecimal(price),
        change = BigDecimal(change),
        changePercent = BigDecimal(changePercent),
        volume = volume,
        timestamp = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME)
    )
}

fun OrderDto.toDomain(): Order {
    return Order(
        id = id,
        ticker = ticker,
        type = when (type) {
            "MARKET" -> OrderType.MARKET
            "LIMIT" -> OrderType.LIMIT
            else -> OrderType.LIMIT
        },
        side = when (side) {
            "BUY" -> OrderSide.BUY
            "SELL" -> OrderSide.SELL
            else -> OrderSide.BUY
        },
        quantity = quantity,
        price = BigDecimal(price),
        status = when (status) {
            "PENDING" -> OrderStatus.PENDING
            "FILLED" -> OrderStatus.FILLED
            "CANCELED" -> OrderStatus.CANCELED
            "REJECTED" -> OrderStatus.REJECTED
            else -> OrderStatus.PENDING
        },
        createdAt = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME),
        updatedAt = LocalDateTime.parse(updatedAt, DateTimeFormatter.ISO_DATE_TIME)
    )
}

fun Order.toDto(): OrderDto {
    return OrderDto(
        id = id,
        ticker = ticker,
        type = when (type) {
            OrderType.MARKET -> "MARKET"
            OrderType.LIMIT -> "LIMIT"
        },
        side = when (side) {
            OrderSide.BUY -> "BUY"
            OrderSide.SELL -> "SELL"
        },
        quantity = quantity,
        price = price.toPlainString(),
        status = when (status) {
            OrderStatus.PENDING -> "PENDING"
            OrderStatus.FILLED -> "FILLED"
            OrderStatus.CANCELED -> "CANCELED"
            OrderStatus.REJECTED -> "REJECTED"
        },
        createdAt = createdAt.format(DateTimeFormatter.ISO_DATE_TIME),
        updatedAt = updatedAt.format(DateTimeFormatter.ISO_DATE_TIME)
    )
}