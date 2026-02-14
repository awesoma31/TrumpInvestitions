package com.trumpinvestitions.gateway.service

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object WebSocketService {
    
    private val quoteConnections = ConcurrentHashMap<DefaultWebSocketServerSession, String>()
    private val orderConnections = ConcurrentHashMap<DefaultWebSocketServerSession, String>()
    
    suspend fun handleQuotesWebSocket(session: DefaultWebSocketServerSession) {
        quoteConnections[session] = "quotes-subscriber"
        
        try {
            session.send("""{"type": "connected", "message": "Connected to quotes stream"}""")
            
            // In a real implementation, this would subscribe to Redis Pub/Sub
            // and forward messages to all connected clients
            session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    // Handle incoming messages if needed
                    val text = frame.readText()
                    // Echo back or handle subscription messages
                    session.send("""{"type": "echo", "message": "$text"}""")
                }
            }
        } catch (e: Exception) {
            println("WebSocket error: ${e.message}")
        } finally {
            quoteConnections.remove(session)
        }
    }
    
    suspend fun handleOrdersWebSocket(session: DefaultWebSocketServerSession, userId: String) {
        orderConnections[session] = userId
        
        try {
            session.send("""{"type": "connected", "message": "Connected to orders stream for user: $userId"}""")
            
            // In a real implementation, this would subscribe to Redis Pub/Sub
            // for order updates specific to this user
            session.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    // Handle incoming messages if needed
                    session.send("""{"type": "echo", "message": "$text"}""")
                }
            }
        } catch (e: Exception) {
            println("WebSocket error: ${e.message}")
        } finally {
            orderConnections.remove(session)
        }
    }
    
    suspend fun broadcastQuote(quote: Map<String, Any>) {
        val message = """{"type": "quote", "data": $quote}"""
        quoteConnections.keys.forEach { session ->
            try {
                session.send(message)
            } catch (e: Exception) {
                println("Failed to send quote to client: ${e.message}")
                quoteConnections.remove(session)
            }
        }
    }
    
    suspend fun broadcastOrderUpdate(userId: String, order: Map<String, Any>) {
        val message = """{"type": "order_update", "data": $order}"""
        orderConnections.filter { it.value == userId }.keys.forEach { session ->
            try {
                session.send(message)
            } catch (e: Exception) {
                println("Failed to send order update to client: ${e.message}")
                orderConnections.remove(session)
            }
        }
    }
}