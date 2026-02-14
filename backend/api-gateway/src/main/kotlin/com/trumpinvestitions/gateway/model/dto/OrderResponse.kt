package com.trumpinvestitions.gateway.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderResponse(
    val id: String,
    val ticker: String,
    val type: String, // "MARKET" or "LIMIT"
    val side: String, // "BUY" or "SELL"
    val quantity: Int,
    val price: Double,
    val status: String, // "PENDING", "FILLED", "CANCELED", "REJECTED"
    val filledQuantity: Int = 0,
    val averagePrice: Double? = null,
    val createdAt: String,
    val updatedAt: String
)