package com.trumpinvestitions.trading.domain.usecase

import com.trumpinvestitions.trading.domain.model.Order
import com.trumpinvestitions.trading.domain.repository.TradingRepository
import javax.inject.Inject

class CreateOrderUseCase @Inject constructor(
    private val tradingRepository: TradingRepository
) {
    suspend operator fun invoke(order: Order): Result<Order> {
        // Валидация данных перед созданием ордера
        if (order.quantity <= 0) {
            return Result.failure(IllegalArgumentException("Quantity must be positive"))
        }
        
        if (order.price.signum() <= 0) {
            return Result.failure(IllegalArgumentException("Price must be positive"))
        }
        
        return tradingRepository.createOrder(order)
    }
}