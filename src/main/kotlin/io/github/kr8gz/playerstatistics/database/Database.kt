package io.github.kr8gz.playerstatistics.database

import com.google.common.collect.ImmutableList
import com.mojang.authlib.GameProfile
import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.access.ServerStatHandlerMixinAccess
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.minecraft.entity.player.PlayerEntity
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

private val ServerStatHandler.uuid
    get() = file.toString()
            .let(FilenameUtils::getBaseName)
            .let(UUID::fromString)

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

    object Initializer {
        private var job: Job? = null
        val inProgress get() = job?.isCompleted == false

        operator fun invoke(server: MinecraftServer) {
            val databaseFile = server.getSavePath(WorldSavePath.ROOT).resolve("${PlayerStatistics.MOD_NAME}.db").toFile()
            DATABASE_URL = "jdbc:sqlite:$databaseFile?foreign_keys=on"
            connection.createStatement().use(Table::createAll)
            connection.createStatement().use(View::createAll)

            // if there are already statistic entries, skip populating the database with player statistics
            if (connection.prepareStatement("SELECT 1 FROM $Statistics LIMIT 1").executeQuery().next()) return

            fun streamStatsFiles() = Files.list(server.getSavePath(WorldSavePath.STATS))

            val fileCount = streamStatsFiles().count().also { if (it == 0L) return }
            val completed = atomic(0)

            job = launch {
                streamStatsFiles().asSequence().forEach { path ->
                    val statHandler = ServerStatHandler(server, path.toFile())
                    // the user cache can only store up to 1000 players by default, but at least it's better than nothing
                    server.userCache?.getByUuid(statHandler.uuid)?.let { if (it.isPresent) Updater.updateProfile(it.get()) }
                    Updater.updateStats(statHandler, changedOnly = false)

                    completed.incrementAndGet()
                }
            }.apply {
                invokeOnCompletion {
                    PlayerStatistics.LOGGER.info("Finished database initialization with $completed/$fileCount players")
                }
            }

            launch {
                while (inProgress) {
                    PlayerStatistics.LOGGER.info("Initializing database ($completed/$fileCount players)")
                    delay(10.seconds)
                }
            }
        }
    }

    object Updater {
        suspend fun updateProfile(profile: GameProfile): Unit = with(Players) {
            coroutineScope {
                connection.prepareStatement("INSERT INTO $Players ($uuid, $name) VALUES (?1, ?2) ON CONFLICT($uuid) DO UPDATE SET $name = ?2").run {
                    setString(1, profile.id.toString())
                    setString(2, profile.name)
                    executeUpdate()
                }
            }
        }

        suspend fun updateStats(statHandler: ServerStatHandler, changedOnly: Boolean = true) = with(Statistics) {
            val statMap = when {
                changedOnly -> (statHandler as ServerStatHandlerMixinAccess).takeChangedStats()
                else -> statHandler.statMap
            }
            if (statMap.isNotEmpty()) coroutineScope {
                connection.prepareStatement("REPLACE INTO $Statistics ($playerUUID, $statistic, $value) VALUES (?, ?, ?)").run {
                    setString(1, statHandler.uuid.toString())
                    statMap.forEach { (stat, value) ->
                        setString(2, stat.name)
                        setInt(3, value)
                        addBatch()
                    }
                    executeBatch()
                }
            }
        }

        @JvmStatic
        fun launchStatsUpdate(statHandler: ServerStatHandler) {
            launch { updateStats(statHandler) }
        }
    }

    // TODO command suggestion list (string argument type instead of game profile -> never multiple, query using join players name
    var playerNames: List<String> = mutableListOf()
        get() = ImmutableList.copyOf(field)
        private set // TODO during init and on profile update

    object Leaderboard {
        data class Entry(val rank: Int, val key: String, val value: Int)

        suspend fun forStat(stat: String, highlight: PlayerEntity? = null, page: Int = 1): List<Entry> = coroutineScope {
            val pageSize = 9 // 1 row less than default chat height for header
            val query = """
                WITH leaderboard AS (
                    SELECT *, ROW_NUMBER() OVER (ORDER BY ${RankedStatistics.rank}, ${Statistics.playerUUID}) pos
                    FROM $RankedStatistics
                    JOIN $Players ON ${Players.uuid} = ${Statistics.playerUUID}
                    WHERE ${Statistics.statistic} = ?
                ),
                highlight AS (SELECT pos highlight FROM leaderboard WHERE ${Statistics.playerUUID} = ? LIMIT 1)
                SELECT
                    ${RankedStatistics.rank}, ${Players.name}, ${Statistics.value},
                    $pageSize - (highlight IS NOT NULL) page_offset
                FROM leaderboard LEFT JOIN highlight
                WHERE pos >  page_offset * ${page - 1} + COALESCE(highlight < page_offset * ${page - 1}, 0)
                  AND pos <= page_offset * $page       + COALESCE(highlight < page_offset * $page,       0)
                   OR pos = highlight
                ORDER BY pos
            """

            connection.prepareStatement(query).run {
                setString(1, stat)
                setString(2, highlight?.uuidAsString)

                executeQuery().use { rs ->
                    generateSequence {
                        rs.takeIf { it.next() }?.run {
                            Entry(getInt(RankedStatistics.rank), getString(Players.name), getInt(Statistics.value))
                        }
                    }.toList()
                }
            }
        }
    }

    suspend fun serverTotal(stat: String): Int = with(Statistics) {
        coroutineScope {
            val sum = "sum"
            connection.prepareStatement("SELECT SUM($value) $sum FROM $Statistics WHERE $statistic = ?").run {
                setString(1, stat)
                executeQuery().use { rs -> rs.next(); rs.getInt(sum) }
            }
        }
    }

    suspend fun statForPlayer(stat: String, uuid: UUID): Int = with(Statistics) {
        coroutineScope {
            connection.prepareStatement("SELECT $value FROM $Statistics WHERE $statistic = ? AND $playerUUID = ?").run {
                setString(1, stat)
                setString(2, uuid.toString())
                executeQuery().use { rs -> if (rs.next()) rs.getInt(value) else 0 }
            }
        }
    }
}
