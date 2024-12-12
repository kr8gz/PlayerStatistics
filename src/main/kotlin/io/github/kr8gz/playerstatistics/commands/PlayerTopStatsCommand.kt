package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.format.Components
import io.github.kr8gz.playerstatistics.format.Components.withPageDisplay
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.text.Texts
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
        val player = Players.fixName(playerName) ?: return sendError(CommandExceptions.UNKNOWN_PLAYER.getMessage(playerName))
        val leaderboard = Leaderboard.forPlayer(player, page) ?: return sendError(CommandExceptions.NO_DATA.getMessage())

        val label = Text.translatable("playerstatistics.command.top", player).build { color = config.colors.text.alt }

        val entries = leaderboard.pageEntries.map { (_, rank, stat, value) ->
            literalText {
                color = config.colors.extra.main
                text(" » "); text(Text.translatable("playerstatistics.command.top.rank", rank)) { color = config.colors.rank.main }
                text(" ");   text(LeaderboardCommand.formatStatNameWithSuggestion(stat)) { color = config.colors.name.main }
                text(" - "); text(stat.formatValueText(value)) { color = config.colors.value.main }
            }
        }

        val header = label.build { bold = true }
        val content = Texts.join(entries, literalText("\n"))
        val shareCode = storeShareData(label, header.withPageDisplay(page, leaderboard.pageCount) newLine content)

        sendFeedback { header newLine content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode) }
        registerPageAction(max = leaderboard.pageCount) { newPage -> sendPlayerTopStats(playerName, newPage) }
    }
}
