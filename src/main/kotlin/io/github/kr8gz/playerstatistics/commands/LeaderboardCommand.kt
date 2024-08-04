package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.extensions.Text.space
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
        statArgument { maybeStat ->
            optionalPlayerArgument { maybePlayer ->
                executes {
                    val stat = maybeStat() ?: throw Exceptions.NO_DATA.create()
                    val player = maybePlayer() ?: source.player?.gameProfile?.name
                    usingDatabase { source.sendLeaderboard(stat, player) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, highlightedName: String?, page: Int = 1) {
        val leaderboard = Leaderboard.forStat(stat, highlightedName, page)
        val statFormatter = StatFormatter(stat)

        val label = run {
            val statName = statFormatter.name.build { color = config.colors.text.alt }
            Text.translatable("playerstatistics.command.leaderboard", statName).build { color = config.colors.text.main }
        }
        val content = label.build {
            when (leaderboard) {
                null -> {
                    newLine()
                    text(Exceptions.NO_DATA.getMessage()) { color = config.colors.listOutput.noData }
                }
                else -> for ((rank, player, value) in leaderboard.pageEntries) text {
                    val highlightedPlayer = player == highlightedName

                    bold = highlightedPlayer
                    color = config.colors.listOutput.extra.altIf(highlightedPlayer)

                    newLine()
                    text(" » ") { bold = false }
                    text("$rank. ") { color = config.colors.rank.altIf(highlightedPlayer) }
                    text(player) {
                        color = config.colors.name.altIf(highlightedPlayer)
                        if (!highlightedPlayer) {
                            hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.highlight"))
                            clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, formatCommand(statFormatter.commandArguments, player))
                        }
                    }
                    text(" - ") { bold = false }
                    text(statFormatter.formatValue(value)) { color = config.colors.value.altIf(highlightedPlayer) }
                }
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            when (leaderboard) {
                null -> content space Components.shareButton(shareCode)
                else -> {
                    registerPageAction(max = leaderboard.pageCount) { sendLeaderboard(stat, highlightedName, it) }
                    content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode)
                }
            }
        }
    }

    fun formatStatNameWithSuggestion(statFormatter: StatFormatter<*>) = statFormatter.name.build {
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
        clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, formatCommand(statFormatter.commandArguments))
    }
}
