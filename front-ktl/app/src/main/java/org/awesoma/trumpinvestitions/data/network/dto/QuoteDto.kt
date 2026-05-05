package org.awesoma.trumpinvestitions.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class QuoteDto(
    val symbol: String,
    val bid: String,
    val ask: String,
    val last: String,
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val close: String? = null,
    val volume: Long? = null,
    val timestamp: String
)

@Serializable
data class QuoteListResponseDto(
    val items: List<QuoteDto>
)
