package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.UUID

object ServerCommandSource {
    val ServerCommandSource.uuid get() = player?.uuid ?: UUID(0, 0)

    fun ServerCommandSource.sendFeedback(message: () -> Text) = sendFeedback(message, false)
}
