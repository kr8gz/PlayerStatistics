package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import java.util.*

typealias PageAction = (suspend ServerCommandSource.(page: Int) -> Unit)?

object OutputManager {
    private data class ShareData(val label: Text, val content: Text)

    private val storedPlayerOutput = HashMap<UUID, ShareData>()

    fun storeOutput(uuid: UUID, header: Text, label: Text) {
        storedPlayerOutput[uuid] = ShareData(header, label)
    }

    fun ServerCommandSource.shareLastStored(player: ServerPlayerEntity) = storedPlayerOutput.remove(player.uuid)?.let {
        val label = Text.empty().append(it.label).styled { style ->
            style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, it.content))
        }
        val message = Text.translatable("playerstatistics.command.share", player.name.string, label)
        server.playerManager.broadcast(message, false)
    } ?: sendError(Text.translatable("playerstatistics.command.share.unavailable"))

    private val pageActions = HashMap<UUID, PageAction>()

    fun registerPageAction(uuid: UUID, action: PageAction) {
        pageActions[uuid] = action
    }

    suspend fun ServerCommandSource.runPageAction(uuid: UUID, page: Int) {
        pageActions[uuid]?.let { action -> action(page) }
            ?: sendError(Text.translatable("playerstatistics.command.page.unavailable"))
    }
}
