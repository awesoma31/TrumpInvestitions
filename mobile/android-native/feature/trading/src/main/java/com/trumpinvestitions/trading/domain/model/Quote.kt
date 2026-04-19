package com.trumpinvestitions.trading.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class Quote(
    val ticker: String,
    val price: BigDecimal,
    val change: BigDecimal,
    val changePercent: BigDecimal,
    val volume: Long,
    val timestamp: LocalDateTime
)