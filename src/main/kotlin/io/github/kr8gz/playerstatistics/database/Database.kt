package io.github.kr8gz.playerstatistics.database

import com.mojang.authlib.GameProfile
import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.access.ServerStatHandlerMixinAccess
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.uuid
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.stat.ServerStatHandler
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.*
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

    private val playerNameMap by lazy {
        with(Players) {
            connection.prepareStatement("SELECT * FROM $Players").executeQuery().use { rs ->
                generateSequence {
                    rs.takeIf { it.next() }?.run { UUID.fromString(getString(uuid)) to getString(name) }
                }.toMap(HashMap())
            }
        }
    }

    val playerNames get() = playerNameMap.values.sorted()

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
                connection.prepareStatement("INSERT INTO $Players ($uuid, $name) VALUES (?, ?) ON CONFLICT($uuid) DO UPDATE SET $name = excluded.$name").run {
                    setString(1, profile.id.toString())
                    setString(2, profile.name)
                    executeUpdate()
                }
                playerNameMap[profile.id] = profile.name
            }
        }

        suspend fun updateStats(statHandler: ServerStatHandler, changedOnly: Boolean = true): Unit = with(Statistics) {
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

    data class Leaderboard<T>(val pageEntries: List<Entry<T>>, val pageCount: Int) {
        data class Entry<T>(val rank: Int, val key: T, val value: Int) {
            companion object {
                /** @return `null` if the player was not found in the database;
                 *          rank = 0 if the value is 0;
                 *          key = player name
                 */
                suspend fun of(stat: Stat<*>, playerName: String): Entry<String>? = coroutineScope {
                    val query = """
                        SELECT ${RankedStatistics.rank}, ${Players.name}, COALESCE(${Statistics.value}, 0) ${Statistics.value}
                        FROM $Players
                        LEFT JOIN $RankedStatistics ON ${Players.uuid} = ${Statistics.playerUUID} AND ${Statistics.statistic} = ?
                        WHERE ${Players.name} = ?
                    """
                    connection.prepareStatement(query).run {
                        setString(1, stat.name)
                        setString(2, playerName)
                        executeQuery()
                    }.use { rs ->
                        rs.takeIf { it.next() }?.run {
                            Entry(getInt(RankedStatistics.rank), getString(Players.name), getInt(Statistics.value))
                        }
                    }
                }
            }
        }

        companion object {
            private const val pageSize = 8 // default chat size 10 minus rows for header and footer

            private inline fun <T> ResultSet.generateLeaderboard(
                crossinline pageCount: ResultSet.() -> Int,
                crossinline entryBuilder: ResultSet.() -> Entry<T>?
            ): Leaderboard<T> {
                var pages: Int? = null
                val entries = mutableListOf<Entry<T>>()
                while (next()) {
                    if (pages == null) pages = pageCount()
                    entryBuilder()?.let(entries::add)
                }
                return Leaderboard(entries, pages.takeIf { entries.isNotEmpty() } ?: 0)
            }

            /** @return key = player name */
            suspend fun forStat(stat: Stat<*>, highlightName: String? = null, page: Int): Leaderboard<String> = coroutineScope {
                val pageCount = "page_count"
                val query = """
                    WITH leaderboard AS (
                        SELECT *, ROW_NUMBER() OVER (ORDER BY ${RankedStatistics.rank}, ${Statistics.playerUUID}) pos
                        FROM $RankedStatistics
                        JOIN $Players ON ${Players.uuid} = ${Statistics.playerUUID}
                        WHERE ${Statistics.statistic} = ?
                    ),
                    highlight AS (SELECT pos highlight FROM leaderboard WHERE ${Players.name} = ? LIMIT 1),
                    page_offset AS (SELECT $pageSize - EXISTS(SELECT 1 FROM highlight) page_offset),
                    $pageCount AS (
                        SELECT CEIL(1.0 * ((SELECT MAX(pos) FROM leaderboard) - (highlight IS NOT NULL)) / page_offset) $pageCount
                        FROM page_offset LEFT JOIN highlight
                    )
                    SELECT ${RankedStatistics.rank}, ${Players.name}, ${Statistics.value}, $pageCount
                    FROM leaderboard LEFT JOIN highlight, page_offset, $pageCount
                    WHERE pos >  page_offset * ${page - 1} + COALESCE(highlight < page_offset * ${page - 1}, 0)
                      AND pos <= page_offset * $page       + COALESCE(highlight < page_offset * $page,       0)
                       OR pos = highlight
                    ORDER BY pos
                """
                connection.prepareStatement(query).run {
                    setString(1, stat.name)
                    setString(2, highlightName)
                    executeQuery()
                }.use { rs ->
                    rs.generateLeaderboard({ getInt(pageCount) }) {
                        Entry(getInt(RankedStatistics.rank), getString(Players.name), getInt(Statistics.value))
                    }
                }
            }

            /** @return key = stat */
            suspend fun forPlayer(name: String, page: Int): Leaderboard<Stat<*>> = coroutineScope {
                val pageCount = "page_count"
                val query = """
                    WITH leaderboard AS (
                        SELECT *, ROW_NUMBER() OVER (ORDER BY ${RankedStatistics.rank}, ${Statistics.value} DESC) pos
                        FROM $RankedStatistics
                        JOIN $Players ON ${Players.uuid} = ${Statistics.playerUUID}
                        WHERE ${Players.name} = ? AND ${Statistics.value} > 0
                    )
                    SELECT
                        ${RankedStatistics.rank}, ${Statistics.statistic}, ${Statistics.value},
                        CEIL(1.0 * (SELECT MAX(pos) FROM leaderboard) / $pageSize) $pageCount
                    FROM leaderboard
                    WHERE pos > ${pageSize * (page - 1)}
                    ORDER BY pos
                    LIMIT $pageSize
                """
                connection.prepareStatement(query).run {
                    setString(1, name)
                    executeQuery()
                }.use { rs ->
                    rs.generateLeaderboard({ getInt(pageCount) }) {
                        getString(Statistics.statistic).split(':').map { Identifier.splitOn(it, '.') }.let { (statTypeId, statId) ->
                            fun <T> StatType<T>.getStat(id: Identifier) = registry[id]?.let(::getOrCreateStat)
                            Registries.STAT_TYPE[statTypeId]?.getStat(statId)
                        }?.let { stat ->
                            Entry(getInt(RankedStatistics.rank), stat, getInt(Statistics.value))
                        }
                    }
                }
            }
        }
    }

    suspend fun serverTotal(stat: Stat<*>): Long = with(Statistics) {
        coroutineScope {
            val sum = "sum"
            connection.prepareStatement("SELECT SUM($value) $sum FROM $Statistics WHERE $statistic = ?")
                .run { setString(1, stat.name); executeQuery() }
                .use { rs -> rs.next(); rs.getLong(sum) }
        }
    }

    suspend fun fixPlayerName(playerName: String): String? = with(Players) {
        coroutineScope {
            connection.prepareStatement("SELECT $name FROM $Players WHERE $name = ?")
                .run { setString(1, playerName); executeQuery() }
                .use { rs -> rs.takeIf { it.next() }?.getString(name) }
        }
    }
}
