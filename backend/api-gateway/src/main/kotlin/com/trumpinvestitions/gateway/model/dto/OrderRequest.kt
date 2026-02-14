package com.trumpinvestitions.gateway.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderRequest(
    val ticker: String,
    val type: String, // "MARKET" or "LIMIT"
    val side: String, // "BUY" or "SELL"
    val quantity: Int,
    val price: Double? = null // Required only for LIMIT orders
)