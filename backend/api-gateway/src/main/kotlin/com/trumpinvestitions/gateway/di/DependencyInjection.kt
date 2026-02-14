package com.trumpinvestitions.gateway.di

import com.trumpinvestitions.gateway.security.JwtService
import io.ktor.server.application.*

fun Application.configureDI() {
    // JWT Service will be configured in Authentication.kt
    // Other dependencies can be added here as needed
    
    // Example of how to configure a database connection:
    // val database = Database.connect(
    //     url = environment.config.property("database.url").getString(),
    //     driver = environment.config.property("database.driver").getString(),
    //     user = environment.config.property("database.user").getString(),
    //     password = environment.config.property("database.password").getString()
    // )
    
    // Example of how to configure Redis:
    // val redis = JedisPool(
    //     environment.config.property("redis.host").getString(),
    //     environment.config.property("redis.port").getString().toInt()
    // )
}