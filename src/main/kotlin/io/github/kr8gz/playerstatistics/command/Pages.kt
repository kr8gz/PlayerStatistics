package io.github.kr8gz.playerstatistics.command

import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.util.*

typealias PageAction = suspend ServerCommandSource.(page: Int) -> Unit

private val pageActions = HashMap<UUID?, PageAction?>()

fun ServerCommandSource.registerPageAction(action: PageAction?) {
    pageActions[id] = action
}

suspend fun ServerCommandSource.runPageAction(page: Int) {
    pageActions[id]?.let { it(page) } ?: sendError(Text.translatable("playerstatistics.command.page.unavailable"))
}
