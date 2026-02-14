package com.trumpinvestitions.gateway.routes

import com.trumpinvestitions.gateway.service.QuotesService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.quotesRoutes() {
    // Get all quotes
    get {
        try {
            val quotes = QuotesService.getAllQuotes()
            call.respond(HttpStatusCode.OK, quotes)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get quotes: ${e.message}")
            )
        }
    }
    
    // Get specific quote by ticker
    get("/{ticker}") {
        try {
            val ticker = call.parameters["ticker"]
                ?: throw IllegalArgumentException("Ticker is required")
            
            val quote = QuotesService.getQuote(ticker)
            call.respond(HttpStatusCode.OK, quote)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to e.message)
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get quote: ${e.message}")
            )
        }
    }
    
    // Get historical quotes
    get("/{ticker}/history") {
        try {
            val ticker = call.parameters["ticker"]
                ?: throw IllegalArgumentException("Ticker is required")
            
            val period = call.request.queryParameters["period"] ?: "1d"
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            
            val history = QuotesService.getHistoricalQuotes(ticker, period, limit)
            call.respond(HttpStatusCode.OK, history)
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to e.message)
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to get historical quotes: ${e.message}")
            )
        }
    }
}