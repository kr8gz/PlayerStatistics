package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.Texts
import java.util.*

private data class ShareData(val label: Text, val content: Text)

private val storedShareData = HashMap<UUID?, ShareData>()

fun ServerCommandSource.storeShareData(label: Text, content: Text) {
    storedShareData[uuid] = ShareData(label, content)
}

fun ServerCommandSource.shareStoredData() {
    storedShareData.remove(uuid)?.let { (label, content) ->
        val hoverText = Texts.bracketed(Text.translatable("playerstatistics.command.share.hover")).styled {
            it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, content))
        }
        val message = Text.translatable("playerstatistics.command.share.message", entity?.displayName ?: name, label)
        server.playerManager.broadcast(message.append(" ").append(hoverText), false)
    } ?: sendError(Text.translatable("playerstatistics.command.share.unavailable"))
}
