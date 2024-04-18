package io.github.kr8gz.playerstatistics.database

import io.github.kr8gz.playerstatistics.access.ServerStatHandlerMixinAccess
import io.github.kr8gz.playerstatistics.extensions.ServerStatHandler.uuid
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

    suspend fun updateStats(statHandler: ServerStatHandler, changedOnly: Boolean = true): Unit = coroutineScope {
        val statMap = when {
            changedOnly -> (statHandler as ServerStatHandlerMixinAccess).takeChangedStats()
            else -> statHandler.statMap
        }
        if (statMap.isNotEmpty()) {
            Database.prepareStatement("REPLACE INTO $Statistics ($player, $stat, $value) VALUES (?, ?, ?)").run {
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
        Database.transaction { updateStats(statHandler) }
    }

    suspend fun serverTotal(stat: Stat<*>): Long = coroutineScope {
        val sum = "sum"
        Database.prepareStatement("SELECT SUM($value) $sum FROM $Statistics WHERE ${Statistics.stat} = ?")
            .run { setString(1, stat.name); executeQuery() }
            .use { rs -> rs.next(); rs.getLong(sum) }
    }
}
