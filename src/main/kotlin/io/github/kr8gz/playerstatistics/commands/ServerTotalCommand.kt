package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.messages.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object ServerTotalCommand : StatsCommand("total") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        statArgument { stat ->
            optionalPlayerArgument { player ->
                executes {
                    usingDatabase { source.sendServerTotal(stat(), player()) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendServerTotal(stat: Stat<*>, highlightName: String?) {
        val statFormatter = StatFormatter(stat)

        val label = run {
            val statName = LeaderboardCommand.formatStatNameWithSuggestion(statFormatter).withColor(Colors.WHITE)
            Text.translatable("playerstatistics.command.total", statName).withColor(Colors.GRAY)
        }
        val content = label.build {
            val total = Statistics.serverTotal(stat)
            text(": "); text(statFormatter.formatValue(total)) { color = Colors.VALUE_HIGHLIGHT }

            highlightName?.let { Leaderboard.Entry(stat, it) }?.takeIf { it.value > 0 }?.let { (_, name, value) ->
                newLine()

                val formattedName = literalText(name) { color = Colors.WHITE }
                val contributed = statFormatter.formatValue(value).build { color = Colors.VALUE_HIGHLIGHT }
                text(Text.translatable("playerstatistics.command.total.contributed", formattedName, contributed)) { color = Colors.GRAY }

                text(" ") {
                    text("(")
                    val percentage = formatNumber(value.toFloat() / total * 100)
                    text("$percentage%") { color = Colors.VALUE }
                    text(")")
                    color = Colors.GRAY
                }
            }
        }

        sendFeedback {
            val shareCode = storeShareData(label, content)
            content space Components.shareButton(shareCode)
        }
    }
}
