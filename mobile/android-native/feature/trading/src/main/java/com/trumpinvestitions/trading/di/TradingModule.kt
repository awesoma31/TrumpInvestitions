package com.trumpinvestitions.trading.di

import com.trumpinvestitions.trading.data.repository.TradingRepositoryImpl
import com.trumpinvestitions.trading.domain.repository.TradingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TradingModule {

    @Binds
    @Singleton
    abstract fun bindTradingRepository(
        tradingRepositoryImpl: TradingRepositoryImpl
    ): TradingRepository
}