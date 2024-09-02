package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.Players
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
        statArgument { maybeStat ->
            optionalPlayerArgument { maybePlayer ->
                executes {
                    val stat = maybeStat() ?: throw Exceptions.NO_DATA.create()
                    val player = maybePlayer() ?: source.player?.gameProfile?.name
                    usingDatabase { source.sendServerTotal(stat, player) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendServerTotal(stat: Stat<*>, highlightedName: String?) {
        val statFormatter = StatFormatter(stat)

        val label = run {
            val statName = LeaderboardCommand.formatStatNameWithSuggestion(statFormatter).build { color = config.colors.text.alt }
            Text.translatable("playerstatistics.command.total", statName).build { color = config.colors.text.main }
        }

        val content = label.build {
            val total = Statistics.serverTotal(stat)
            text(": "); text(statFormatter.formatValue(total)) { color = config.colors.value.alt }

            if (total > 0) highlightedName?.let { Statistics.singleValue(stat, it) }?.let { value ->
                newLine()
                val formattedName = literalText(Players.fixName(highlightedName)) { color = config.colors.text.alt }
                val contributed = statFormatter.formatValue(value).build { color = config.colors.value.alt }
                text(Text.translatable("playerstatistics.command.total.contributed", formattedName, contributed)) {
                    val percentage = formatNumber(value.toFloat() / total * 100)
                    text(" ("); text("$percentage%") { color = config.colors.value.main }; text(")")
                    color = config.colors.text.main
                }
            }
        }

        val shareCode = storeShareData(label, content)
        sendFeedback { content space Components.shareButton(shareCode) }
    }
}
