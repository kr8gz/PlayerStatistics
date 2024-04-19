package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.command.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.Components
import io.github.kr8gz.playerstatistics.messages.formatName
import io.github.kr8gz.playerstatistics.messages.formatNumber
import io.github.kr8gz.playerstatistics.messages.formatValue
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
        val label = Text.translatable("playerstatistics.command.total", stat.formatName())
        val content = literalText {
            val total = Statistics.serverTotal(stat)
            text(label); text(": "); text(stat.formatValue(total))

            player?.let { player ->
                Leaderboard.Entry(stat, player.gameProfile.name)?.takeIf { it.value > 0 }?.let { (_, _, value) ->
                    newLine()
                    text(Text.translatable("playerstatistics.command.total.contributed",
                        player.displayName, stat.formatValue(value), formatNumber(value.toFloat() / total * 100),
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
