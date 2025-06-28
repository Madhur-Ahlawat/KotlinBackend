package org.kotlinbackend.models.response

import kotlinx.serialization.Serializable

@Serializable
data class FileResponse(
    val id: Int,
    val fileName: String,
    val contentType: String,
    val uploadedAt: String  // Send as ISO string
)