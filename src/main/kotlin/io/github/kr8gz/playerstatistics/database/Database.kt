package io.github.kr8gz.playerstatistics.database

import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.uuid
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.minecraft.server.MinecraftServer
import net.minecraft.stat.ServerStatHandler
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Statement
import kotlin.streams.asSequence
import kotlin.time.Duration.Companion.seconds

object Database : CoroutineScope {
    override val coroutineContext = Dispatchers.IO

    private lateinit var DATABASE_URL: String

    private val connection by object {
        var connection: Connection? = null

        operator fun getValue(thisRef: Any?, property: Any?): Connection {
            return connection?.takeUnless { it.isClosed }
                ?: DriverManager.getConnection(DATABASE_URL).also { connection = it }
        }
    }

    fun transaction(block: suspend Database.() -> Unit) = launch {
        Database.block()
        connection.commit()
    }

    // don't expose the connection object
    fun prepareStatement(sql: String): PreparedStatement = connection.prepareStatement(sql)

    private var initializerJob: Job? = null
    val isInitializing get() = initializerJob?.isCompleted == false

    fun initialize(server: MinecraftServer) {
        DATABASE_URL = server.getSavePath(WorldSavePath.ROOT)
            .resolve("${PlayerStatistics.MOD_NAME}.db").toFile()
            .let { "jdbc:sqlite:$it?foreign_keys=on" }

        Schema.createAll(Players, Statistics, Leaderboard)

        val existingEntries = prepareStatement("SELECT 1 FROM $Statistics LIMIT 1").executeQuery().next()
        connection.autoCommit = false

        if (existingEntries) return

        fun streamStatsFiles() = Files.list(server.getSavePath(WorldSavePath.STATS))

        val fileCount = streamStatsFiles().count().also { if (it == 0L) return }
        val completed = atomic(0)

        initializerJob = transaction {
            streamStatsFiles().asSequence().forEach { path ->
                val statHandler = ServerStatHandler(server, path.toFile())
                // the user cache can only store up to 1000 players by default, but at least it's better than nothing
                server.userCache?.getByUuid(statHandler.uuid)?.let { if (it.isPresent) Players.updateProfile(it.get()) }
                Statistics.updateStats(statHandler, changedOnly = false)

                completed.incrementAndGet()
            }
        }.apply {
            invokeOnCompletion {
                PlayerStatistics.LOGGER.info("Finished database initialization with $completed/$fileCount players")
            }
        }

        launch {
            while (isInitializing) {
                PlayerStatistics.LOGGER.info("Initializing database ($completed/$fileCount players)")
                delay(10.seconds)
            }
        }
    }

    private interface Schema {
        fun create(statement: Statement)

        companion object {
            fun createAll(vararg schema: Schema) {
                connection.createStatement().use { statement ->
                    schema.forEach { it.create(statement) }
                }
            }
        }
    }

    abstract class Table(private val name: String) : Schema {
        override fun toString() = name

        protected abstract val schema: List<String>

        override fun create(statement: Statement) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS $name (${schema.joinToString()})")
        }
    }

    abstract class View(private val name: String) : Schema {
        override fun toString() = name

        protected abstract val definition: String

        override fun create(statement: Statement) {
            statement.executeUpdate("CREATE VIEW IF NOT EXISTS $name AS $definition")
        }
    }
}
