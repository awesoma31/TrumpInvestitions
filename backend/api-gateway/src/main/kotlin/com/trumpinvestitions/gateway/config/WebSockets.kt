package com.trumpinvestitions.gateway.config

import com.trumpinvestitions.gateway.service.WebSocketService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        webSocket("/ws/quotes") {
            WebSocketService.handleQuotesWebSocket(this)
        }
        
        webSocket("/ws/orders/{userId}") {
            val userId = call.parameters["userId"] ?: return@webSocket
            WebSocketService.handleOrdersWebSocket(this, userId)
        }
    }
}