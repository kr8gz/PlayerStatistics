package io.github.kr8gz.playerstatistics.util

import io.github.kr8gz.playerstatistics.PlayerStatistics
import net.minecraft.registry.Registries
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.util.Identifier

fun parseStat(s: String): Stat<*>? = try {
    val (statTypeId, statId) = s.split(':').map { Identifier.splitOn(it, '.') }
    fun <T> StatType<T>.getStat() = registry[statId]?.let(::getOrCreateStat)
    Registries.STAT_TYPE[statTypeId]?.getStat()
} catch (e: RuntimeException) {
    PlayerStatistics.LOGGER.warn("Unknown statistic: $s")
    null
}
