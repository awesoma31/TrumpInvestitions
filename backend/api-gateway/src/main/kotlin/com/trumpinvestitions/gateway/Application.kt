package com.trumpinvestitions.gateway

import com.trumpinvestitions.gateway.config.configureAuthentication
import com.trumpinvestitions.gateway.config.configureHTTP
import com.trumpinvestitions.gateway.config.configureRouting
import com.trumpinvestitions.gateway.config.configureSerialization
import com.trumpinvestitions.gateway.config.configureStatusPages
import com.trumpinvestitions.gateway.config.configureWebSockets
import com.trumpinvestitions.gateway.di.configureDI
import com.trumpinvestitions.gateway.plugins.configureRateLimit
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    // Инициализация зависимостей
    configureDI()
    
    // Конфигурация сервера
    configureSerialization()
    configureHTTP()
    configureAuthentication()
    configureStatusPages()
    configureRateLimit()
    configureRouting()
    configureWebSockets()
}