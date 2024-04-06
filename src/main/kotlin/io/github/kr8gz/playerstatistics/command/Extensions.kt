package io.github.kr8gz.playerstatistics.command

import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.minecraft.util.Identifier

val ServerCommandSource.uuid get() = player?.uuid

fun ServerCommandSource.sendFeedback(message: Text) = sendFeedback({ message }, false)

val StatType<*>.identifier: Identifier get() = Registries.STAT_TYPE.getKey(this).get().value

fun <T> Stat<T>.asCommandArguments(): String {
    val stat = type.registry.getKey(value).get().value
    if (type == Stats.CUSTOM) return stat.toString()
    return "${type.identifier.path} $stat"
}
