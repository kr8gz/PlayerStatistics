package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.messages.Colors
import io.github.kr8gz.playerstatistics.messages.Components
import io.github.kr8gz.playerstatistics.messages.StatFormatter
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder

object LeaderboardCommand : StatsCommand("leaderboard") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        statArgument { stat ->
            executes {
                usingDatabase { source.sendLeaderboard(stat()) }
            }
        }
    }

    private suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, page: Int = 1) {
        val highlightName = player?.gameProfile?.name
        val leaderboard = Leaderboard.forStat(stat, highlightName, page)
        val statFormatter = StatFormatter(stat)

        val statName = statFormatter.name.build { color = Colors.WHITE }
        val label = Text.translatable("playerstatistics.command.leaderboard", statName).withColor(Colors.GRAY)

        val content = label.build {
            leaderboard.pageEntries.forEach { (rank, player, value) ->
                text {
                    val highlightPlayer = player == highlightName
                    fun highlight(color: Int) = color.takeIf { highlightPlayer }

                    bold = highlightPlayer
                    color = highlight(Colors.GRAY) ?: Colors.DARK_GRAY

                    text("\n » ")   { bold = false }
                    text("$rank. ") { color = highlight(Colors.GREEN) ?: Colors.GOLD }
                    text(player)    { color = highlight(Colors.WHITE) ?: Colors.YELLOW }
                    text(" - ")     { bold = false }
                    text(statFormatter.formatValue(value)) { color = highlight(Colors.VALUE_HIGHLIGHT) ?: Colors.VALUE }
                }
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode)
        }
        registerPageAction(max = leaderboard.pageCount) { sendLeaderboard(stat, it) }
    }
}
