package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.messages.Components
import io.github.kr8gz.playerstatistics.messages.StatFormatter
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object PlayerTopStatsCommand : StatsCommand("top") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        optionalPlayerArgument { maybePlayer ->
            executes {
                val player = maybePlayer() ?: source.playerOrThrow.gameProfile.name
                usingDatabase { source.sendPlayerTopStats(player) }
            }
        }
    }

    private suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
        val leaderboard = Leaderboard.forPlayer(playerName, page)
            ?: return sendError(Exceptions.UNKNOWN_PLAYER.getMessage(playerName))

        val label = run {
            val player = Players.fixName(playerName) ?: playerName
            Text.translatable("playerstatistics.command.top", player).build { color = config.colors.text.alt }
        }
        val content = literalText {
            text(label.copy()) { bold = true }
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                val statFormatter = StatFormatter(stat)
                color = config.colors.extra.main

                newLine()
                text(" » "); text(Text.translatable("playerstatistics.command.top.rank", rank)) { color = config.colors.rank.main }
                text(" ");   text(LeaderboardCommand.formatStatNameWithSuggestion(statFormatter)) { color = config.colors.name.main }
                text(" - "); text(statFormatter.formatValue(value)) { color = config.colors.value.main }
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode)
        }
        registerPageAction(max = leaderboard.pageCount) { sendPlayerTopStats(playerName, it) }
    }
}
