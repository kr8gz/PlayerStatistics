package io.github.kr8gz.playerstatistics.extensions

import io.github.kr8gz.playerstatistics.PlayerStatistics
import net.minecraft.util.Identifier

object Identifier {
    fun Identifier.toShortString(): String = when (namespace) {
        Identifier.DEFAULT_NAMESPACE, PlayerStatistics.MOD_ID -> path
        else -> toString()
    }
}
