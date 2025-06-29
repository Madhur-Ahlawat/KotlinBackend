package org.kotlinbackend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.kotlinbackend.utils.Constants
import org.kotlinbackend.utils.Constants.userId
import java.util.*

object JwtConfig {
    private const val secret = "secret123"   // Change this in production
    private const val issuer = "ktor.io"
    private const val audience = "ktorAudience"
    private const val refreshTokenExpiry = 7 * 24 * 60 * 60 * 1000 //7 days
    private const val accessTokenExpiry = 15 * 60 * 1000 //15 minutes

    private val algorithm = Algorithm.HMAC256(secret)

    fun generateAccessToken(userId:Int,username: String): String = JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim(Constants.userId, userId)
        .withClaim(Constants.username, username)
        .withExpiresAt(Date(System.currentTimeMillis() + accessTokenExpiry)) // 15 min expiry
        .sign(algorithm)

    fun generateRefreshToken(userId:Int,username: String): String = JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim(Constants.userId, userId)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenExpiry)) // 7 days
        .sign(algorithm)

    fun getVerifier(): JWTVerifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
}