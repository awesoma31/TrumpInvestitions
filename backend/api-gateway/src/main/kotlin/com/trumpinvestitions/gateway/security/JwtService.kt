package com.trumpinvestitions.gateway.security

import io.jsonwebtoken.*
import io.ktor.server.application.*
import java.util.*

class JwtService(environment: ApplicationEnvironment) {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()
    
    val verifier: JWTVerifier = JwtVerifierBuilder()
        .requireAudience(audience)
        .requireIssuer(issuer)
        .buildWithSecret(secret)
    
    fun generateToken(userId: String, email: String): String {
        return Jwts.builder()
            .setIssuer(issuer)
            .setAudience(audience)
            .setSubject("Authentication")
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .claim("userId", userId)
            .claim("email", email)
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact()
    }
    
    fun verifyToken(token: String): JWTPrincipal? {
        return try {
            val jwt = Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
            
            JWTPrincipal(jwt.body)
        } catch (e: Exception) {
            null
        }
    }
}