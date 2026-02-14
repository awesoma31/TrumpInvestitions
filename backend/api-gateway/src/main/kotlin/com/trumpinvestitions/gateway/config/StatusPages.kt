package com.trumpinvestitions.gateway.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception: ${cause.message}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error")
            )
        }
        
        exception<IllegalArgumentException> { call, cause ->
            call.application.log.warn("Bad request: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Bad request"))
            )
        }
        
        statusFile(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                mapOf("error" to "Not found")
            )
        }
        
        statusFile(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                mapOf("error" to "Unauthorized")
            )
        }
        
        statusFile(HttpStatusCode.Forbidden) { call, status ->
            call.respond(
                status,
                mapOf("error" to "Forbidden")
            )
        }
    }
}