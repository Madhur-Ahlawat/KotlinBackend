package org.kotlinbackend.routes

import io.ktor.http.*
import io.ktor.http.content.*
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
import org.kotlinbackend.db.Files
import org.kotlinbackend.db.RefreshTokens
import org.kotlinbackend.db.Users
import org.kotlinbackend.models.request.User
import org.kotlinbackend.models.response.ServerResponse
import org.kotlinbackend.utils.Constants
import org.kotlinbackend.utils.Constants.application_octet_stream
import org.kotlinbackend.utils.Constants.invalid_refresh_token
import org.kotlinbackend.utils.Constants.logged_out
import org.kotlinbackend.utils.Constants.refresh_token_missing
import org.kotlinbackend.utils.Constants.server_running
import org.kotlinbackend.utils.Constants.signup_succesful
import org.kotlinbackend.utils.Constants.upload_valid_file
import org.kotlinbackend.utils.Constants.userId
import org.kotlinbackend.utils.Constants.user_exists
import org.kotlinbackend.utils.Constants.username
import org.kotlinbackend.utils.Endpoints
import org.kotlinbackend.utils.Endpoints.signup
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Application.initRoutes() {
    routing {
        post(signup) {
            val userData = call.receive<User>()
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
        //Auth APIs
        post(Endpoints.login) {
            val userData = call.receive<User>()
            val userRow = transaction {
                Users.select {
                    (Users.username eq userData.username) and (Users.password eq userData.password)
                }.singleOrNull()
            }

            if (userRow != null) {
                val userId = userRow[Users.id]
                val accessToken = JwtConfig.generateAccessToken(userId.value, userData.username)
                val refreshToken = JwtConfig.generateRefreshToken(userId.value, userData.username)

                transaction {
                    RefreshTokens.deleteWhere { RefreshTokens.userId eq userId.value }
                    RefreshTokens.insert {
                        it[RefreshTokens.userId] = userId.value
                        it[RefreshTokens.token] = refreshToken
                    }
                }

                call.respond(
                    mapOf(
                        Constants.accessToken to accessToken,
                        Constants.refreshToken to refreshToken
                    )
                )
            } else {
                call.respondText(text=Constants.invalid_credentials,status = HttpStatusCode.Forbidden)
            }
        }
        post(Endpoints.refresh) {
            val request = call.receive<Map<String, String>>()
            val refreshToken = request[Constants.refreshToken]
            if (refreshToken == null) {
                call.respondText(refresh_token_missing, status = io.ktor.http.HttpStatusCode.BadRequest)
                return@post
            }

            try {
                val decodedJWT = JwtConfig.getVerifier().verify(refreshToken)
                val username = decodedJWT.getClaim(Constants.username).asString()
                val tokenRow = transaction {
                    RefreshTokens.select { RefreshTokens.token eq refreshToken }.singleOrNull()
                }

                if (tokenRow != null) {
                    val userId = tokenRow[RefreshTokens.userId]

                    // ✅ Proceed with generating new access token / refresh token for this userId
                    val newAccessToken = JwtConfig.generateAccessToken(userId, username)
                    val newRefreshToken = JwtConfig.generateRefreshToken(userId, username)

                    transaction {
                        // Optional: Delete old refresh token if implementing refresh token rotation
                        RefreshTokens.deleteWhere { RefreshTokens.token eq refreshToken }

                        // Save new refresh token
                        RefreshTokens.insert {
                            it[RefreshTokens.userId] = userId
                            it[RefreshTokens.token] = newRefreshToken
                        }
                    }

                    call.respond(
                        mapOf(
                            Constants.accessToken to newAccessToken,
                            Constants.refreshToken to newRefreshToken
                        )
                    )
                }
                else {
                    call.respond(HttpStatusCode.Unauthorized, Constants.invalid_refresh_token)
                }

            } catch (e: Exception) {
                call.respondText(invalid_refresh_token, status = io.ktor.http.HttpStatusCode.Unauthorized)
            }
        }

        authenticate(Constants.authJwt) {
            get(Endpoints.profile) {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim(username).asString()

                call.respond(status= HttpStatusCode.OK, ServerResponse(message="Welcome $username! This is your profile."))
            }
        }

        //File handling APIs
        authenticate(Constants.authJwt) {
            post(Endpoints.upload) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim(userId).asInt()
                val multipart = call.receiveMultipart()
                var fileName: String? = null
                var contentType: String? = null
                var fileContent: ByteArray? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            fileName = part.originalFileName
                            contentType = part.contentType?.toString() ?: application_octet_stream
                            fileContent = part.streamProvider().readBytes()
                        }

                        else -> Unit
                    }
                    part.dispose()
                }

                if (fileName != null && fileContent != null) {
                    transaction {
                        Files.insert {
                            it[Files.fileName] = fileName!!
                            it[Files.contentType] = contentType!!
                            it[Files.fileData] = fileContent!!
                            it[Files.uploadedBy] = userId
                        }
                    }
                    call.respond(status = HttpStatusCode.OK, ServerResponse(message="File '$fileName' uploaded!"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, upload_valid_file)
                }
            }
        }
        authenticate(Constants.authJwt) {
            get(Endpoints.file_exists_on_server) {
                val fileNameParam = call.request.queryParameters[Constants.fileName]

                if (fileNameParam.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ServerResponse(message = Constants.missing_file_name))
                    return@get
                }

                val fileExists = transaction {
                    Files.select { Files.fileName eq fileNameParam }.count() > 0
                }

                if (fileExists) {
                    call.respond(status = HttpStatusCode.OK, message = ServerResponse(message = "✅ File '$fileNameParam' exists on server!"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ServerResponse(message = "❌ File '$fileNameParam' not found on server!"))
                }
            }
            get(Endpoints.home) {
                call.respondText(server_running)
            }
            post(Endpoints.logout) {
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
                    call.respondText(
                        "Refresh token not found. Already invalid?",
                        status = io.ktor.http.HttpStatusCode.Gone
                    )
                }
            }
        }
        authenticate(Constants.authJwt) {
            get(Endpoints.list_user_files) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim(userId).asInt()  // ✅ Extract userId from JWT

                val userFiles = transaction {
                    Files.select { Files.uploadedBy eq userId }.map {
                        ServerResponse(
                            id = it[Files.id].value,  // ✅ Int
                            fileName = it[Files.fileName],
                            contentType = it[Files.contentType],
                            uploadedAt = it[Files.uploadedAt].toString()
                        )
                    }
                }
                call.respond(userFiles)
            }
        }
        authenticate(Constants.authJwt) {
            get(Endpoints.download_file) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim(Constants.userId).asInt()
                val fileName = call.request.queryParameters[Constants.fileName]
                if (fileName == null) {
                    call.respond(HttpStatusCode.BadRequest, ServerResponse(message = Constants.file_not_found))
                    return@get
                }

                val fileRow = transaction {
                    Files.select {
                        (Files.fileName eq fileName) and (Files.uploadedBy eq userId)
                    }.singleOrNull()
                }

                if (fileRow == null) {
                    call.respond(HttpStatusCode.NotFound, ServerResponse(message = Constants.file_not_found))
                    return@get
                }

                val contentType = fileRow[Files.contentType]
                val fileData = fileRow[Files.fileData]

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"$fileName\""
                )

                call.respondBytes(fileData, ContentType.parse(contentType))
            }
        }
        authenticate(Constants.authJwt) {
            get(Endpoints.download_all_files) {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal!!.payload.getClaim(Constants.userId).asInt()
            val username = transaction {
                Users.select { Users.id eq userId }
                    .singleOrNull()
                    ?.get(Users.username)
            } ?: "user"
            val userFiles = transaction {
                Files.select { Files.uploadedBy eq userId }.toList()
            }

            if (userFiles.isEmpty()) {
                call.respond(HttpStatusCode.NotFound, ServerResponse(message = Constants.no_file_available))
                return@get
            }
            val zipBytes = ByteArrayOutputStream().use { baos ->
                ZipOutputStream(baos).use { zos ->
                    userFiles.forEach { fileRow ->
                        val fileName = fileRow[Files.fileName]
                        val fileData = fileRow[Files.fileData]
                        val zipEntry = ZipEntry(fileName)
                        zos.putNextEntry(zipEntry)
                        zos.write(fileData)
                        zos.closeEntry()
                    }
                }
                baos.toByteArray()
            }
            call.response.header(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${username}_files.zip\""
            )
            call.respondBytes(zipBytes, ContentType.Application.Zip)
        } }
        authenticate(Constants.authJwt) {
            delete(Endpoints.delete_file) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim(Constants.userId).asInt()
                val fileName = call.request.queryParameters[Constants.fileName]
                if (fileName.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ServerResponse(message = Constants.file_name_missing))
                    return@delete
                }
                val rowsDeleted = transaction {
                    Files.deleteWhere {
                        (Files.fileName eq fileName) and (Files.uploadedBy eq userId)
                    }
                }
                if (rowsDeleted > 0) {
                    call.respond(HttpStatusCode.OK, ServerResponse(fileName=fileName, message = Constants.deleted))
                } else {
                    call.respond(HttpStatusCode.NotFound, ServerResponse(fileName=fileName, message = Constants.not_found))
                }
            }
        }
        authenticate(Constants.authJwt) {
            get(Endpoints.stream_video) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim(Constants.userId).asInt()

                val filename = call.request.queryParameters[Constants.fileName]?:"file"
                if (filename == null) {
                    call.respond(HttpStatusCode.BadRequest, Constants.invalid_file_id)
                    return@get
                }

                val fileRow = transaction {
                    Files.select {
                        (Files.fileName eq filename) and (Files.uploadedBy eq userId)
                    }.singleOrNull()
                }

                if (fileRow == null) {
                    call.respond(HttpStatusCode.NotFound, Constants.video_not_found)
                    return@get
                }

                val fileData = fileRow[Files.fileData]
                val contentType = fileRow[Files.contentType]

                val rangeHeader = call.request.headers[Constants.range]
                val totalLength = fileData.size

                if (rangeHeader == null) {
                    // No Range header → send full file
                    call.respondBytes(
                        fileData,
                        ContentType.parse(contentType),
                        HttpStatusCode.OK
                    )
                } else {
                    // Handle Range Request
                    val rangeRegex = Regex("bytes=(\\d+)-(\\d*)")
                    val matchResult = rangeHeader.let { rangeRegex.find(it) }

                    if (matchResult != null) {
                        val start = matchResult.groupValues[1].toInt()
                        val end = matchResult.groupValues[2].toIntOrNull() ?: (totalLength - 1)

                        if (start >= totalLength || end >= totalLength || start > end) {
                            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                            return@get
                        }

                        val contentRange = "bytes $start-$end/$totalLength"
                        val length = end - start + 1
                        val partialContent = fileData.sliceArray(start..end)

                        call.response.header(HttpHeaders.AcceptRanges, Constants.bytes)
                        call.response.header(HttpHeaders.ContentRange, contentRange)

                        call.respondBytes(
                            partialContent,
                            ContentType.parse(contentType),
                            HttpStatusCode.PartialContent
                        )
                    } else {
                        call.respond(HttpStatusCode.BadRequest, ServerResponse(message = Constants.invalid_range_header))
                    }
                }
            }
        }
        authenticate(Constants.authJwt) {
            get(Endpoints.stream_audio) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal!!.payload.getClaim(Constants.userId).asInt()

                val fileName = call.request.queryParameters[Constants.fileName]
                if (fileName == null) {
                    call.respond(HttpStatusCode.BadRequest, Constants.invalid_file_id)
                    return@get
                }

                val fileRow = transaction {
                    Files.select {
                        (Files.fileName eq fileName) and (Files.uploadedBy eq userId)
                    }.singleOrNull()
                }

                if (fileRow == null) {
                    call.respond(HttpStatusCode.NotFound, Constants.file_not_found)
                    return@get
                }

                val fileData = fileRow[Files.fileData]
                val contentType = fileRow[Files.contentType]

                val rangeHeader = call.request.headers[Constants.range]
                val totalLength = fileData.size

                if (rangeHeader == null) {
                    // Full file
                    call.respondBytes(
                        fileData,
                        ContentType.parse(contentType),
                        HttpStatusCode.OK
                    )
                } else {
                    // Partial streaming
                    val rangeRegex = Regex("bytes=(\\d+)-(\\d*)")
                    val matchResult = rangeHeader.let { rangeRegex.find(it) }

                    if (matchResult != null) {
                        val start = matchResult.groupValues[1].toInt()
                        val end = matchResult.groupValues[2].toIntOrNull() ?: (totalLength - 1)

                        if (start >= totalLength || end >= totalLength || start > end) {
                            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                            return@get
                        }

                        val contentRange = "bytes $start-$end/$totalLength"
                        val length = end - start + 1
                        val partialContent = fileData.sliceArray(start..end)

                        call.response.header(HttpHeaders.AcceptRanges, Constants.bytes)
                        call.response.header(HttpHeaders.ContentRange, contentRange)

                        call.respondBytes(
                            partialContent,
                            ContentType.parse(contentType),
                            HttpStatusCode.PartialContent
                        )
                    } else {
                        call.respond(HttpStatusCode.BadRequest, Constants.invalid_range_header)
                    }
                }
            }
        }
    }
}