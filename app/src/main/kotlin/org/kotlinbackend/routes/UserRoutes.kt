package org.kotlinbackend.routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kotlinbackend.auth.JwtConfig
import org.kotlinbackend.db.RefreshTokens
import org.kotlinbackend.db.RefreshTokens.token
import org.kotlinbackend.db.UserDTO
import org.kotlinbackend.db.Users
import org.kotlinbackend.db.Users.password
import org.kotlinbackend.utils.Constants
import org.kotlinbackend.utils.Constants.authJwt
import org.kotlinbackend.utils.Constants.invalid_credentials
import org.kotlinbackend.utils.Constants.invalid_refresh_token
import org.kotlinbackend.utils.Constants.logged_out
import org.kotlinbackend.utils.Constants.refresh_token_missing
import org.kotlinbackend.utils.Constants.server_running
import org.kotlinbackend.utils.Constants.signup_succesful
import org.kotlinbackend.utils.Constants.user_exists
import org.kotlinbackend.utils.Endpoints.login
import org.kotlinbackend.utils.Endpoints.profile
import org.kotlinbackend.utils.Endpoints.refresh
import org.kotlinbackend.utils.Endpoints.signup

// Import your models, DB, etc.
fun Application.initRoutes(){
    routing {  post(signup) {
        val userData = call.receive<UserDTO>()

        val userExists = transaction {
            Users.select { Users.username eq userData.username }.count() > 0
        }

        if (userExists) {
            call.respondText(user_exists, status = io.ktor.http.HttpStatusCode.Conflict)
        } else {
            transaction {
                Users.insert {
                    it[username] = userData.username
                    it[password] = userData.password
                }
            }
            call.respondText(signup_succesful)
        }
    }

        post(login) {
            val userData = call.receive<UserDTO>()

            val isValidUser = transaction {
                Users.select {
                    (Users.username eq userData.username) and (password eq userData.password)
                }.count() > 0
            }

            if (isValidUser) {
                val accessToken = JwtConfig.generateAccessToken(userData.username)
                val refreshToken = JwtConfig.generateRefreshToken(userData.username)

                transaction {
                    RefreshTokens.insert {
                        it[username] = userData.username
                        it[token] = refreshToken
                    }
                }

                call.respond(mapOf(
                    Constants.accessToken to accessToken,
                    Constants.refreshToken to refreshToken
                ))
            } else {
                call.respondText(invalid_credentials, status = io.ktor.http.HttpStatusCode.Unauthorized)
            }
        }

        post(refresh) {
            val request = call.receive<Map<String, String>>()
            val refreshToken = request[Constants.refreshToken]

            if (refreshToken == null) {
                call.respondText(refresh_token_missing, status = io.ktor.http.HttpStatusCode.BadRequest)
                return@post
            }

            try {
                val decodedJWT = JwtConfig.getVerifier().verify(refreshToken)
                val username = decodedJWT.getClaim("username").asString()

                val tokenExists = transaction {
                    RefreshTokens.select {
                        (RefreshTokens.username eq username) and (token eq refreshToken)
                    }.count() > 0
                }

                if (!tokenExists) {
                    call.respondText(invalid_refresh_token, status = io.ktor.http.HttpStatusCode.Unauthorized)
                    return@post
                }

                val newAccessToken = JwtConfig.generateAccessToken(username)
                call.respond(mapOf(
                    Constants.accessToken to newAccessToken
                ))

            } catch (e: Exception) {
                call.respondText(invalid_refresh_token, status = io.ktor.http.HttpStatusCode.Unauthorized)
            }
        }

        get("/") {
            call.respondText(server_running)
        }

        authenticate(authJwt) {
            get(profile) {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()

                call.respondText("Welcome $username! This is your profile.")
            }
        }

        post("/logout") {
            val request = call.receive<Map<String, String>>()
            val refreshToken = request["refreshToken"]

            if (refreshToken == null) {
                call.respondText(refresh_token_missing, status = io.ktor.http.HttpStatusCode.BadRequest)
                return@post
            }

            // Delete the refresh token from DB
            val deletedCount = transaction {
                RefreshTokens.deleteWhere { RefreshTokens.token eq refreshToken }
            }

            if (deletedCount > 0) {
                call.respondText(logged_out)
            } else {
                call.respondText("Refresh token not found. Already invalid?", status = io.ktor.http.HttpStatusCode.Gone)
            }
        } }
}