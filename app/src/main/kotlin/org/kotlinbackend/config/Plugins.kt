package org.kotlinbackend.config

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.sessions.*
import kotlinx.serialization.json.Json
import org.kotlinbackend.auth.JwtConfig
import org.kotlinbackend.models.UserSession
import org.kotlinbackend.utils.Constants
import org.kotlinbackend.utils.Constants.USER_SESSION
import org.kotlinbackend.utils.Constants.authJwt
import org.slf4j.event.Level

fun Application.installPlugins() {

    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(Sessions) {
        cookie<UserSession>(USER_SESSION) {
            cookie.path = "/"
            cookie.httpOnly = true
        }
    }

    install(Authentication) {
        jwt(authJwt) {
            verifier(JwtConfig.getVerifier())
            validate { credential ->
                if (credential.payload.getClaim(Constants.username).asString().isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }
}