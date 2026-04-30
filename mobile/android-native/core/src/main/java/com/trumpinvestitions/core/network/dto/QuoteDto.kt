package com.trumpinvestitions.core.network.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Serializable
data class QuoteDto(
    val ticker: String,
    val price: String,
    val change: String,
    val changePercent: String,
    val volume: Long,
    val timestamp: String
)