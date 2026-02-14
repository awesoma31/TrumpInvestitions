package com.trumpinvestitions.gateway.config

import com.trumpinvestitions.gateway.security.JwtService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuthentication() {
    val jwtService = JwtService(environment)
    
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtService.realm
            verifier(jwtService.verifier)
            
            validate { credential ->
                if (credential.payload.audience.contains(jwtService.audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            
            challenge { defaultScheme, realm ->
                call.respond(UnauthorizedResponse())
            }
        }
    }
}