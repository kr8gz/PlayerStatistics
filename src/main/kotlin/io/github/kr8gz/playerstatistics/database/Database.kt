package io.github.kr8gz.playerstatistics.database

import com.mojang.authlib.GameProfile
import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.database.Table.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.minecraft.server.MinecraftServer
import net.minecraft.stat.ServerStatHandler
import net.minecraft.util.WorldSavePath
import org.apache.commons.io.FilenameUtils
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.streams.asSequence
import kotlin.time.Duration.Companion.seconds

private val ServerStatHandler.uuidString
    get() = FilenameUtils.getBaseName(file.toString())

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

    private var initializationJob: Job? = null
    val isInitializing get() = initializationJob?.isCompleted == false

    fun initialize(server: MinecraftServer) {
        val databaseFile = server.getSavePath(WorldSavePath.ROOT).resolve("${PlayerStatistics.MOD_NAME}.db").toFile()
        DATABASE_URL = "jdbc:sqlite:$databaseFile?foreign_keys=on"
        connection.createStatement().use(Table::createAll)

        // if there are already statistic entries, skip populating the database with player statistics
        if (connection.prepareStatement("SELECT 1 FROM $Statistics LIMIT 1").executeQuery().next()) return

        fun streamStatisticsFiles() = Files.list(server.getSavePath(WorldSavePath.STATS))

        val fileCount = streamStatisticsFiles().count().also { if (it == 0L) return }
        val completed = atomic(0)

        initializationJob = launch {
            streamStatisticsFiles().asSequence().forEach { path ->
                val statHandler = ServerStatHandler(server, path.toFile())
                val uuid = UUID.fromString(statHandler.uuidString)
                // the user cache can only store up to 1000 players by default, but at least it's better than nothing
                server.userCache?.getByUuid(uuid)?.ifPresent(::updatePlayerProfile)
                updatePlayerStatistics(statHandler)
                
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

    fun updatePlayerProfile(profile: GameProfile): Unit = with(Players) {
        connection.prepareStatement("INSERT INTO $Players ($uuid, $name) VALUES (?, ?) ON CONFLICT($uuid) DO UPDATE SET $name = ?").run {
            setString(1, profile.id.toString())
            setString(2, profile.name)
            setString(3, profile.name)
            executeUpdate()
        }
    }

    @JvmSynthetic
    suspend fun updatePlayerStatistics(statHandler: ServerStatHandler): Unit = with(Statistics) {
        if (!statHandler.statMap.isEmpty()) coroutineScope {
            connection.prepareStatement("REPLACE INTO $Statistics ($playerUUID, $statistic, $value) VALUES (?, ?, ?)").run {
                setString(1, statHandler.uuidString)
                statHandler.statMap.forEach { (stat, value) ->
                    setString(2, stat.name)
                    setInt(3, value)
                    addBatch()
                }
                executeBatch()
            }
        }
    }

    @JvmStatic
    fun launchUpdatePlayerStatistics(statHandler: ServerStatHandler) {
        launch { updatePlayerStatistics(statHandler) }
    }
}
