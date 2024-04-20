package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.Components
import io.github.kr8gz.playerstatistics.messages.StatFormatter
import io.github.kr8gz.playerstatistics.messages.formatNumber
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object ServerTotalCommand : StatsCommand("total") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        statArgument { stat ->
            executes {
                usingDatabase { source.sendServerTotal(stat()) }
            }
        }
    }

    private suspend fun ServerCommandSource.sendServerTotal(stat: Stat<*>) {
        val statFormatter = StatFormatter(stat)

        val label = Text.translatable("playerstatistics.command.total", statFormatter.name)
        val content = literalText {
            val total = Statistics.serverTotal(stat)
            text(label); text(": "); text(statFormatter.formatValue(total))

            player?.let { player ->
                Leaderboard.Entry(stat, player.gameProfile.name)?.takeIf { it.value > 0 }?.let { (_, _, value) ->
                    newLine()
                    text(Text.translatable("playerstatistics.command.total.contributed",
                        player.displayName, statFormatter.formatValue(value), formatNumber(value.toFloat() / total * 100),
                    ))
                }
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            content space Components.shareButton(shareCode)
        }
    }
}
