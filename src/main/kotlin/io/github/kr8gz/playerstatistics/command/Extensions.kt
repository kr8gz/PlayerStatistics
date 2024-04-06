package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

val ServerCommandSource.id get() = player?.uuid

fun ServerCommandSource.sendFeedback(message: Text) = sendFeedback({ message }, false)
