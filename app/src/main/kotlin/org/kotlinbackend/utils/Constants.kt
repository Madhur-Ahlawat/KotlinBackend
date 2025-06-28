package org.kotlinbackend.utils

object Constants{
    //DB related
    const val maximumPoolSize = 10
    const val db_username = "postgres"
    const val db_password = "admin"
    const val jdbcurl = "jdbc:postgresql://localhost:5432/"
    const val db_name = "RhythmDB"
    const val driverClassName = "org.postgresql.Driver"
    //Table string messages
    const val connected_to_db = "✅ Connected to PostgreSQL databse"
    const val created_table = "✅ Created table"
    //Session
    const val USER_SESSION = "USER_SESSION"
    //JWT token
    const val authJwt = "auth-jwt"
    const val accessToken = "accessToken"
    const val refreshToken = "refreshToken"
    const val contentType = "contentType"
    const val uploadedAt = "uploadedAt"
    //Common strings
    const val username = "username"
    const val password = "password"
    const val fileName = "fileName"
    const val userId = "userId"
    //DB strings
    const val token = "token"
    const val file_name = "file_name"
    const val content_type = "content_type"
    const val file_data = "file_data"
    const val uploaded_at = "uploaded_at"
    const val uploaded_by = "uploaded_by"
    const val user_id = "user_id"
    const val id = "id"

    //Messages
    const val refresh_token_missing = "Refresh token missing!"
    const val invalid_refresh_token = "Invalid refresh token!"
    const val invalid_credentials = "Invalid credentials!"
    const val user_exists = "User already exists!"
    const val signup_succesful = "Signup successful!"
    const val server_running = "Server is running!"
    const val logged_out = "Logged out successfully!"
    const val upload_valid_file = "Please upload valid file!"
    const val missing_file_name = "Missing file name in path!"
    const val application_octet_stream = "application/octet-stream"
}