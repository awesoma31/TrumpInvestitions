package com.trumpinvestitions.trading.domain.usecase

import com.trumpinvestitions.trading.domain.model.Quote
import com.trumpinvestitions.trading.domain.repository.TradingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetQuotesUseCase @Inject constructor(
    private val tradingRepository: TradingRepository
) {
    operator fun invoke(): Flow<List<Quote>> {
        return tradingRepository.getQuotes()
    }
}