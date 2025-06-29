package org.kotlinbackend.db

import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    val username: String,
    val password: String
)