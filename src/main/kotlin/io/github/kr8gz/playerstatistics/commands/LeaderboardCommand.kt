package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.Colors
import io.github.kr8gz.playerstatistics.messages.Components
import io.github.kr8gz.playerstatistics.messages.StatFormatter
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder

object LeaderboardCommand : StatsCommand("leaderboard") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        statArgument { stat ->
            optionalPlayerArgument {
                executes {
                    val player = it() ?: throw ServerCommandSource.REQUIRES_PLAYER_EXCEPTION.create()
                    usingDatabase { source.sendLeaderboard(stat(), player) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, highlightName: String, page: Int = 1) {
        val leaderboard = Leaderboard.forStat(stat, highlightName, page)
        val hasEntries = leaderboard.pageCount > 0

        val statFormatter = StatFormatter(stat)

        val label = run {
            val statName = statFormatter.name.build { color = Colors.WHITE }
            Text.translatable("playerstatistics.command.leaderboard", statName).withColor(Colors.GRAY)
        }
        val content = label.build {
            for ((rank, player, value) in leaderboard.pageEntries) text {
                val highlightPlayer = player == highlightName
                fun highlight(color: Int) = color.takeIf { highlightPlayer }

                bold = highlightPlayer
                color = highlight(Colors.GRAY) ?: Colors.DARK_GRAY

                newLine()
                text(" » ")     { bold = false }
                text("$rank. ") { color = highlight(Colors.GREEN) ?: Colors.GOLD }
                text(player)    { color = highlight(Colors.WHITE) ?: Colors.YELLOW }
                text(" - ")     { bold = false }
                text(statFormatter.formatValue(value)) { color = highlight(Colors.VALUE_HIGHLIGHT) ?: Colors.VALUE }
            }
            if (!hasEntries) {
                newLine()
                text(Exceptions.NO_DATA.getMessage()) { color = Colors.RED }
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            when {
                hasEntries -> content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode)
                else -> content space Components.shareButton(shareCode)
            }
        }
        registerPageAction(max = leaderboard.pageCount) { sendLeaderboard(stat, highlightName, it) }
    }

    fun formatStatNameWithSuggestion(statFormatter: StatFormatter<*>) = statFormatter.name.build {
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
        clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, formatCommand(statFormatter.commandArguments))
    }
}
