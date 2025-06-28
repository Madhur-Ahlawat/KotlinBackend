package org.kotlinbackend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.kotlinbackend.config.installPlugins
import org.kotlinbackend.db.Files
import org.kotlinbackend.db.RefreshTokens
import org.kotlinbackend.db.Users
import org.kotlinbackend.routes.initRoutes
import org.kotlinbackend.utils.Constants
import org.kotlinbackend.utils.Constants.connected_to_db
import org.kotlinbackend.utils.Constants.created_table
import org.kotlinbackend.utils.Constants.db_name
import org.kotlinbackend.utils.Constants.db_password
import org.kotlinbackend.utils.Constants.db_username

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    installPlugins()
    initRoutes()
    initDatabase()
    createTables()
}

fun initDatabase(){
    val config = HikariConfig().apply {
        jdbcUrl = Constants.jdbcurl + db_name
        driverClassName = Constants.driverClassName
        username = db_username
        password = db_password
        maximumPoolSize = Constants.maximumPoolSize
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)
    println(connected_to_db + db_name)
}

fun createTables() {
    transaction {
        SchemaUtils.create(Users, RefreshTokens, Files)
        println(created_table + "Users, RefreshTokens, Files")
    }
}
