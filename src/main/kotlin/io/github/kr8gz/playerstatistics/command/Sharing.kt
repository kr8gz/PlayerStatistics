package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import java.util.*

private data class ShareData(val label: Text, val content: Text)

private val storedShareData = HashMap<UUID?, ShareData>()

fun ServerCommandSource.storeShareData(header: Text, label: Text) {
    storedShareData[id] = ShareData(header, label)
}

fun ServerCommandSource.shareStoredData() = storedShareData.remove(id)?.let {
    val label = Text.empty().append(it.label).styled { style ->
        style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, it.content))
    }
    val message = Text.translatable("playerstatistics.command.share", entity?.displayName ?: name, label)
    server.playerManager.broadcast(message, false)
} ?: sendError(Text.translatable("playerstatistics.command.share.unavailable"))
