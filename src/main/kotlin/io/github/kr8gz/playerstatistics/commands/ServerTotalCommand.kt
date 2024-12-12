package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.format.*
import io.github.kr8gz.playerstatistics.util.StatSource
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object ServerTotalCommand : StatsCommand("total") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        statArgument { maybeStat ->
            optionalPlayerArgument { maybePlayer ->
                executes {
                    val stat = maybeStat() ?: throw CommandExceptions.NO_DATA.create()
                    val player = maybePlayer() ?: source.player?.gameProfile?.name
                    usingDatabase { source.sendServerTotal(stat, player) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendServerTotal(stat: StatSource, highlightedName: String?) {
        val label = run {
            val statName = LeaderboardCommand.formatStatNameWithSuggestion(stat).build { color = config.colors.text.alt }
            Text.translatable("playerstatistics.command.total", statName).build { color = config.colors.text.main }
        }

        val content = label.build {
            val total = Statistics.serverTotal(stat)
            text(": "); text(stat.formatValueText(total)) { color = config.colors.value.alt }

            if (total == 0L || highlightedName == null) return@build
            val name = Players.fixName(highlightedName) ?: return@build
            val value = Statistics.singleValue(stat, name) ?: return@build

            newLine()
            val formattedName = literalText(name) { color = config.colors.text.alt }
            val contributed = stat.formatValueText(value).build { color = config.colors.value.alt }
            text(Text.translatable("playerstatistics.command.total.contributed", formattedName, contributed)) {
                val percentage = formatDecimalString(value.toFloat() / total * 100)
                text(" ("); text("$percentage%") { color = config.colors.value.main }; text(")")
                color = config.colors.text.main
            }
        }

        val shareCode = storeShareData(label, content)
        sendFeedback { content space Components.shareButton(shareCode) }
    }
}
