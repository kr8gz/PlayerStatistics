package io.github.kr8gz.playerstatistics.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private typealias PageAction = suspend ServerCommandSource.(page: Int) -> Unit

object PageCommand : StatsCommand("page") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        argument(Arguments.PAGE, IntegerArgumentType.integer(1)) { page ->
            executes {
                usingDatabase { source.runPageAction(page()) }
            }
        }
    }

    private val pageActions = ConcurrentHashMap<UUID, PageAction?>()

    fun ServerCommandSource.registerPageAction(max: Int, action: PageAction?) {
        pageActions[uuid] = action?.let {
            { page ->
                if (page <= max) action(page)
                else sendError(Text.translatable("playerstatistics.command.page.no_data"))
            }
        }
    }

    private suspend fun ServerCommandSource.runPageAction(page: Int) {
        pageActions[uuid]?.let { it(page) } ?: sendError(Text.translatable("playerstatistics.command.page.unavailable"))
    }
}
