package com.trumpinvestitions.gateway.config

import com.trumpinvestitions.gateway.routes.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Public routes (no authentication required)
        route("/api/v1/public") {
            authenticate("auth-jwt", optional = true) {
                rateLimit(RateLimitName("public")) {
                    get("/health") {
                        // Health check endpoint
                    }
                }
            }
        }
        
        // Protected routes (authentication required)
        route("/api/v1") {
            authenticate("auth-jwt") {
                rateLimit(RateLimitName("protected")) {
                    // Trading routes
                    tradingRoutes()
                    
                    // Portfolio routes
                    portfolioRoutes()
                    
                    // Analytics routes
                    analyticsRoutes()
                    
                    // User routes
                    userRoutes()
                }
            }
        }
        
        // Quotes routes (high rate limit)
        route("/api/v1/quotes") {
            authenticate("auth-jwt") {
                rateLimit(RateLimitName("quotes")) {
                    quotesRoutes()
                }
            }
        }
    }
}