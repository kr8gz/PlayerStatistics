package io.github.kr8gz.playerstatistics.database

import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.takeChangedStats
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.uuid
import kotlinx.coroutines.coroutineScope
import net.minecraft.registry.Registries
import net.minecraft.stat.ServerStatHandler
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.util.Identifier

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

    fun parseStat(s: String): Stat<*>? {
        return try {
            val (statTypeId, statId) = s.split(':').map { Identifier.splitOn(it, '.') }
            fun <T> StatType<T>.getStat(id: Identifier) = registry[id]?.let(::getOrCreateStat)
            Registries.STAT_TYPE[statTypeId]?.getStat(statId)
        } catch (e: RuntimeException) {
            PlayerStatistics.LOGGER.warn("Unknown statistic: $s")
            null
        }
    }

    val statList by Database.Initializer {
        prepareStatement("SELECT DISTINCT $stat FROM $Statistics").executeQuery().use { rs ->
            generateSequence {
                rs.takeIf { it.next() }?.getString(stat)
            }.mapNotNull(::parseStat).toHashSet()
        }
    }

    suspend fun updateStats(statHandler: ServerStatHandler, changedOnly: Boolean = true): Unit = coroutineScope {
        val statMap = with(statHandler) { if (changedOnly) takeChangedStats() else statMap }
        if (statMap.isEmpty()) return@coroutineScope

        Database.prepareStatement("REPLACE INTO $Statistics ($player, $stat, $value) VALUES (?, ?, ?)").run {
            setString(1, statHandler.uuid.toString())
            statMap.forEach { (stat, value) ->
                setString(2, stat.name)
                setInt(3, value)
                addBatch()
                statList += stat
            }
            executeBatch()
        }
    }

    @JvmStatic
    fun launchStatsUpdate(statHandler: ServerStatHandler) {
        Database.transaction { updateStats(statHandler) }
    }

    suspend fun serverTotal(stat: Stat<*>): Long = coroutineScope {
        val sum = "sum"
        Database.prepareStatement("SELECT SUM($value) $sum FROM $Statistics WHERE ${Statistics.stat} = ?")
            .run { setString(1, stat.name); executeQuery() }
            .use { rs -> rs.next(); rs.getLong(sum) }
    }
}
