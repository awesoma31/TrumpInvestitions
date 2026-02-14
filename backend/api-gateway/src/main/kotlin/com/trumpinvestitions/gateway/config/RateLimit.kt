package com.trumpinvestitions.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun Application.configureRateLimit() {
    install(RateLimit) {
        register(RateLimitName("public")) {
            rateLimiter(limiter = { _, _ ->
                RateLimiter(5, 60.seconds.toJavaDuration())
            })
            requestKey { request ->
                request.origin.remoteHost
            }
        }
        
        register(RateLimitName("protected")) {
            rateLimiter(limiter = { _, _ ->
                RateLimiter(100, 60.seconds.toJavaDuration())
            })
            requestKey { request ->
                request.origin.remoteHost
            }
        }
        
        register(RateLimitName("quotes")) {
            rateLimiter(limiter = { _, _ ->
                RateLimiter(1000, 60.seconds.toJavaDuration())
            })
            requestKey { request ->
                request.origin.remoteHost
            }
        }
    }
}