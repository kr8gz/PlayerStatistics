package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource

val ServerCommandSource.id get() = player?.uuid
