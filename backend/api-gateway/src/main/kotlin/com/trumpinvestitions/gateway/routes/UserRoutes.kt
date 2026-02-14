package com.trumpinvestitions.gateway.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes() {
    route("/user") {
        // Get user profile
        get("/profile") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                // This will be implemented to call the User Service
                // For now, return a placeholder response
                call.respond(HttpStatusCode.OK, mapOf(
                    "userId" to userId,
                    "email" to "user@example.com",
                    "name" to "John Doe",
                    "createdAt" to "2023-01-01T00:00:00Z"
                ))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get user profile: ${e.message}")
                )
            }
        }
        
        // Get user balances
        get("/balances") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                // This will be implemented to call the Trading Service
                // For now, return a placeholder response
                call.respond(HttpStatusCode.OK, mapOf(
                    "userId" to userId,
                    "balances" to emptyList<Any>(),
                    "totalBalance" to 0.0
                ))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get user balances: ${e.message}")
                )
            }
        }
        
        // Get transaction history
        get("/transactions") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                    ?: throw IllegalArgumentException("User ID not found in token")
                
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                
                // This will be implemented to call the Trading Service
                // For now, return a placeholder response
                call.respond(HttpStatusCode.OK, mapOf(
                    "userId" to userId,
                    "transactions" to emptyList<Any>(),
                    "limit" to limit,
                    "offset" to offset,
                    "total" to 0
                ))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to get transaction history: ${e.message}")
                )
            }
        }
    }
}