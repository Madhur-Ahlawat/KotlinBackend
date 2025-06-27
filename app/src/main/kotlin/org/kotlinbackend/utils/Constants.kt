package org.kotlinbackend.utils

object Constants{
    //DB related
    const val maximumPoolSize = 10
    const val db_username = "postgres"
    const val db_password = "admin"
    const val jdbcurl = "jdbc:postgresql://localhost:5432/"
    const val db_name = "RhythmDB"
    const val driverClassName = "org.postgresql.Driver"
    //Table related
    const val connected_to_db = "✅ Connected to PostgreSQL databse"
    const val created_table = "✅ Created table"
    //session
    const val USER_SESSION = "USER_SESSION"
    //JWT token
    const val authJwt = "auth-jwt"
    const val accessToken = "accessToken"
    const val refreshToken = "refreshToken"
    //Messages
    const val refresh_token_missing = "Refresh token missing!"
    const val invalid_refresh_token = "Invalid refresh token!"
    const val invalid_credentials = "Invalid credentials!"
    const val user_exists = "User already exists!"
    const val signup_succesful = "Signup successful!"
    const val server_running = "Server is running!"
    const val logged_out = "Logged out successfully!"
}