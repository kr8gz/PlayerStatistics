package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

typealias PageAction = suspend ServerCommandSource.(page: Int) -> Unit

private val pageActions = ConcurrentHashMap<UUID, PageAction?>()

fun ServerCommandSource.registerPageAction(max: Int, action: PageAction?) {
    pageActions[uuid] = action?.let {
        { page ->
            if (page <= max) action(page)
            else sendError(Text.translatable("playerstatistics.command.page.no_data"))
        }
    }
}

suspend fun ServerCommandSource.runPageAction(page: Int) {
    pageActions[uuid]?.let { it(page) } ?: sendError(Text.translatable("playerstatistics.command.page.unavailable"))
}
