package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.*

typealias PageAction = suspend ServerCommandSource.(page: Int) -> Unit

private val pageActions = HashMap<UUID?, PageAction?>()

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
