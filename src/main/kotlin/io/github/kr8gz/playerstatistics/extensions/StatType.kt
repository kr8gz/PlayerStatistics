package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.registry.Registries
import net.minecraft.stat.StatType
import net.minecraft.util.Identifier

object StatType {
    val StatType<*>.identifier: Identifier get() = Registries.STAT_TYPE.getKey(this).get().value
}
