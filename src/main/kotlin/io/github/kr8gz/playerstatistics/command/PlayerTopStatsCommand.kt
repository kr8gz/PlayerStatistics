package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.command.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.command.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object PlayerTopStatsCommand : StatsCommand("top") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        playerArgument(optional = true) {
            executes {
                val player = it() // don't throw in coroutine
                usingDatabase { source.sendPlayerTopStats(player) }
            }
        }
    }

    private suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
        val leaderboard = Leaderboard.forPlayer(playerName, page).takeIf { it.pageCount > 0 }
            ?: return sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))

        val label = Text.translatable("playerstatistics.command.top", Players.fixName(playerName))
        val content = literalText {
            text(label)
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                text("\n» ") { color = Colors.DARK_GRAY }
                text(Text.translatable("playerstatistics.command.player.rank", rank) space literalText {
                    text(stat.formatName())
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
                    clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, LeaderboardCommand.format(stat.asCommandArguments()))
                })
                text(" - "); text(stat.formatValue(value))
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode)
        }
        registerPageAction(max = leaderboard.pageCount) { sendPlayerTopStats(playerName, it) }
    }
}
