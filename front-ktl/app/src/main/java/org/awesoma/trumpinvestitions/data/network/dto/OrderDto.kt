package org.awesoma.trumpinvestitions.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: String,
    val userId: Long = 0,
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: Int,
    val filledQuantity: Int? = null,
    val avgFillPrice: String? = null,
    val status: String,
    val rejectionReason: String? = null,
    val createdAt: String,
    val filledAt: String? = null,
    val cancelledAt: String? = null,
    val updatedAt: String
)

@Serializable
data class CreateOrderRequestDto(
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: Int
)

@Serializable
data class OrderListResponseDto(
    val items: List<OrderDto>,
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0
)
