package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.Components
import io.github.kr8gz.playerstatistics.messages.StatFormatter
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object PlayerStatCommand : StatsCommand("player") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        playerArgument { player ->
            statArgument { stat ->
                executes {
                    usingDatabase { source.sendPlayerStat(stat(), player()) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendPlayerStat(stat: Stat<*>, playerName: String) {
        val (rank, player, value) = Leaderboard.Entry(stat, playerName)
            ?: return sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))

        val statFormatter = StatFormatter(stat)

        val label = Text.translatable("playerstatistics.command.player", player, statFormatter.name)
        val content = literalText("$player: ") {
            text(statFormatter.formatValue(value) space statFormatter.name)
            if (rank > 0) {
                text(" "); text(literalText {
                    text("(")
                    text(Text.translatable("playerstatistics.command.player.rank", rank))
                    text(")")
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
                    clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, LeaderboardCommand.format(statFormatter.commandArguments))
                })
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            content space Components.shareButton(shareCode)
        }
    }
}
