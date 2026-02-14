package com.trumpinvestitions.gateway.routes

import com.trumpinvestitions.gateway.model.dto.OrderRequest
import com.trumpinvestitions.gateway.model.dto.OrderResponse
import com.trumpinvestitions.gateway.service.TradingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tradingRoutes() {
    route("/trading") {
        // Get all orders for the authenticated user
        get("/orders") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                val orders = TradingService.getOrders(userId)
                call.respond(HttpStatusCode.OK, orders)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get orders: ${e.message}")
                )
            }
        }
        
        // Get specific order by ID
        get("/orders/{orderId}") {
            try {
                val orderId = call.parameters["orderId"]
                    ?: throw IllegalArgumentException("Order ID is required")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                val order = TradingService.getOrder(orderId, userId)
                call.respond(HttpStatusCode.OK, order)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get order: ${e.message}")
                )
            }
        }
        
        // Create new order
        post("/orders") {
            try {
                val orderRequest = call.receive<OrderRequest>()
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                val order = TradingService.createOrder(orderRequest, userId)
                call.respond(HttpStatusCode.Created, order)
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to create order: ${e.message}")
                )
            }
        }
        
        // Cancel order
        delete("/orders/{orderId}") {
            try {
                val orderId = call.parameters["orderId"]
                    ?: throw IllegalArgumentException("Order ID is required")
                
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                TradingService.cancelOrder(orderId, userId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Order cancelled successfully"))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to cancel order: ${e.message}")
                )
            }
        }
    }
}