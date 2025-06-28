package org.kotlinbackend.models.response

import kotlinx.serialization.Serializable

@Serializable
data class ServerResponse(
    var id: Int? = -1,
    var fileName: String? = null,
    var contentType: String? = null,
    var uploadedAt: String? = null,
    var message: String? = null
)