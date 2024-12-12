package io.github.kr8gz.playerstatistics.database

import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.takeChangedStats
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.uuid
import io.github.kr8gz.playerstatistics.util.StatSource
import io.github.kr8gz.playerstatistics.util.parseStat
import kotlinx.coroutines.coroutineScope
import net.minecraft.stat.ServerStatHandler
import net.minecraft.stat.Stat

object Statistics : Database.Table("statistics") {
    const val player = "player"
    const val stat = "stat"
    const val value = "value"

    override val schema = listOf(
        "$player BINARY(16) NOT NULL",
        "$stat TEXT NOT NULL",
        "$value INT NOT NULL",
        "PRIMARY KEY ($player, $stat)",
    )

    private val _storedStats by Database.Initializer {
        executeQuery("SELECT DISTINCT $stat FROM $Statistics").use { rs ->
            generateSequence {
                rs.takeIf { it.next() }?.getString(stat)
            }.mapNotNull(::parseStat).toHashSet()
        }
    }
    val storedStats: Set<Stat<*>> get() = _storedStats

    suspend fun updateStats(statHandler: ServerStatHandler, changedOnly: Boolean = true): Unit = coroutineScope {
        val statMap = with(statHandler) { if (changedOnly) takeChangedStats() else statMap }
        if (statMap.isEmpty()) return@coroutineScope

        Database.prepareStatement { "REPLACE INTO $Statistics ($player, $stat, $value) VALUES (?, ?, ?)" }.run {
            setString(1, statHandler.uuid.toString())
            statMap.forEach { (stat, value) ->
                setString(2, stat.name)
                setInt(3, value)
                addBatch()
                _storedStats += stat
            }
            executeBatch()
        }
    }

    @JvmStatic
    fun launchStatsUpdate(statHandler: ServerStatHandler) {
        Database.transaction { updateStats(statHandler) }
    }

    suspend fun serverTotal(stat: StatSource): Long = coroutineScope {
        val sum = "sum"
        Database.prepareStatement { "SELECT SUM($value) $sum FROM $Statistics WHERE ${Statistics.stat} = ${param(stat.databaseKey)}" }
            .executeQuery().use { rs -> rs.next(); rs.getLong(sum) }
    }

    suspend fun singleValue(stat: StatSource, playerName: String): Int? = coroutineScope {
        Database.prepareStatement { """
            SELECT $value
            FROM $Players
            LEFT JOIN $Leaderboard ON ${Players.uuid} = $player AND ${Statistics.stat} = ${param(stat.databaseKey)}
            WHERE ${Players.name} = ${param(playerName)}
        """ }.executeQuery().use { rs -> rs.takeIf { it.next() }?.getInt(value) }
    }
}
