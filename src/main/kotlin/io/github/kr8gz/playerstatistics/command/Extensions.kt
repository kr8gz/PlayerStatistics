package io.github.kr8gz.playerstatistics.command

import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.StatType
import net.minecraft.text.Text
import net.minecraft.util.Identifier

val ServerCommandSource.uuid get() = player?.uuid

fun ServerCommandSource.sendFeedback(message: Text) = sendFeedback({ message }, false)

val StatType<*>.identifier: Identifier get() = Registries.STAT_TYPE.getKey(this).get().value

fun Identifier.toShortString(): String = if (namespace == Identifier.DEFAULT_NAMESPACE) path else this.toString()
