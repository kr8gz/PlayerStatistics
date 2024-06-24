package io.github.kr8gz.playerstatistics.database

import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.uuid
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.minecraft.server.MinecraftServer
import net.minecraft.stat.ServerStatHandler
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.sql.*
import kotlin.io.path.exists
import kotlin.streams.asSequence
import kotlin.time.Duration.Companion.seconds

object Database {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private lateinit var URL: String

    private val connection by object {
        var connection: Connection? = null

        operator fun getValue(thisRef: Any?, property: Any?): Connection {
            return connection?.takeUnless { it.isClosed } ?: DriverManager.getConnection(URL).apply {
                autoCommit = false
                connection = this
            }
        }
    }

    fun transaction(block: suspend Database.() -> Unit): Job {
        return coroutineScope.launch { Database.block() }.apply {
            invokeOnCompletion { connection.commit() }
        }
    }

    // don't expose the connection object
    fun prepareStatement(sql: String): PreparedStatement = connection.prepareStatement(sql)

    class Initializer<T : Any>(block: suspend Statement.() -> T) {
        private lateinit var value: T
        operator fun getValue(thisRef: Any?, property: Any?) = value

        init {
            listeners += { value = block() }
        }

        companion object {
            private var job: Job? = null
            val isCompleted get() = job?.isCompleted == true

            private val listeners = mutableListOf<suspend Statement.() -> Unit>()

            fun run(server: MinecraftServer) {
                URL = server.getSavePath(WorldSavePath.ROOT)
                    .resolve("${PlayerStatistics.MOD_NAME}.db").toFile()
                    .let { "jdbc:sqlite:$it?foreign_keys=on" }

                job = transaction {
                    connection.createStatement().use { statement ->
                        listeners.forEach { it(statement) }
                        val hasExistingEntries = statement.executeQuery("SELECT 1 FROM $Statistics LIMIT 1").next()
                        if (!hasExistingEntries) populateTables(server)
                    }
                }.apply {
                    invokeOnCompletion {
                        PlayerStatistics.LOGGER.info("Finished database initialization")
                    }
                }
            }

            private suspend fun populateTables(server: MinecraftServer) {
                val statsPath = server.getSavePath(WorldSavePath.STATS).takeIf { it.exists() } ?: return
                val fileCount = Files.list(statsPath).count().takeIf { it > 0 } ?: return
                val completedFiles = atomic(0)

                coroutineScope.launch {
                    while (!isCompleted) {
                        PlayerStatistics.LOGGER.info("Populating tables ($completedFiles/$fileCount players)")
                        delay(5.seconds)
                    }
                }

                Files.list(statsPath).asSequence().forEach { path ->
                    val statHandler = ServerStatHandler(server, path.toFile())
                    // the user cache can only store up to 1000 players by default, but at least it's better than nothing
                    server.userCache?.getByUuid(statHandler.uuid)?.let { if (it.isPresent) Players.updateProfile(it.get()) }
                    Statistics.updateStats(statHandler, changedOnly = false)

                    completedFiles.incrementAndGet()
                }
            }
        }
    }

    sealed class Object(private val name: String) {
        final override fun toString() = name

        protected abstract fun createSQL(): String

        init {
            Initializer { executeUpdate(createSQL()) }
        }
    }

    abstract class Table(name: String) : Object(name) {
        protected abstract val schema: List<String>
        override fun createSQL() = "CREATE TABLE IF NOT EXISTS $this (${schema.joinToString()})"
    }

    abstract class View(name: String) : Object(name) {
        protected abstract val definition: String
        override fun createSQL() = "CREATE VIEW IF NOT EXISTS $this AS $definition"
    }
}
