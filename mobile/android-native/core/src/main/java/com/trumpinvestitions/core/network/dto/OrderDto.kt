package com.trumpinvestitions.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: String,
    val ticker: String,
    val type: String,
    val side: String,
    val quantity: Int,
    val price: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateOrderRequest(
    val ticker: String,
    val type: String,
    val side: String,
    val quantity: Int,
    val price: String
)