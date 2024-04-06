package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.*

typealias PageAction = (suspend ServerCommandSource.(Int) -> Unit)?

object OutputManager {
    private val storedPlayerOutput = HashMap<UUID, Text>()

    fun storeOutput(uuid: UUID, message: Text) {
        storedPlayerOutput[uuid] = message
    }

    fun ServerCommandSource.shareLastStored(uuid: UUID) {
        storedPlayerOutput.remove(uuid)?.let {
            server.playerManager.broadcast(it, false)
        } ?: sendError(Text.translatable("playerstatistics.command.share.unavailable"))
    }

    private val pageActions = HashMap<UUID, PageAction>()

    fun registerPageAction(uuid: UUID, action: PageAction) {
        pageActions[uuid] = action
    }

    suspend fun ServerCommandSource.runPageAction(uuid: UUID, page: Int) {
        pageActions[uuid]?.let { action -> action(page) }
            ?: sendError(Text.translatable("playerstatistics.command.page.unavailable"))
    }
}
