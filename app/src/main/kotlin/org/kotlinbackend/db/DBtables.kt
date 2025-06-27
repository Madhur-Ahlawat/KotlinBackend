
package org.kotlinbackend.db
import org.jetbrains.exposed.sql.Table

object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 64)
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50)
    val token = varchar("token", 512)
    override val primaryKey = PrimaryKey(id)
}