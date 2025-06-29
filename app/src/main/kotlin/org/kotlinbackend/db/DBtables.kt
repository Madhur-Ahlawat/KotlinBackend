package org.kotlinbackend.db
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.kotlinbackend.utils.Constants
import org.kotlinbackend.utils.Constants.content_type
import org.kotlinbackend.utils.Constants.file_data
import org.kotlinbackend.utils.Constants.file_name
import org.kotlinbackend.utils.Constants.uploaded_at
import org.kotlinbackend.utils.Constants.uploaded_by
import java.time.LocalDateTime

object Users : IntIdTable() {
    val username = varchar(Constants.username, 50).uniqueIndex()
    val password = varchar(Constants.password, 64)
}

object RefreshTokens : IntIdTable() {
    val userId = integer(Constants.user_id).references(Users.id)
    val token = varchar(Constants.token, 512)
}

object Files : IntIdTable() {
    val fileName = varchar(file_name, 255)
    val contentType = varchar(content_type, 255)
    val fileData = binary(file_data)
    val uploadedAt = datetime(uploaded_at).clientDefault { LocalDateTime.now() }
    val uploadedBy = reference(uploaded_by,Users)  //Foreign key association
}
