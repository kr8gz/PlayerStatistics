package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.util.Identifier

object Identifier {
    fun Identifier.toShortString(): String = if (namespace == Identifier.DEFAULT_NAMESPACE) path else this.toString()
}
