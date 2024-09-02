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
import net.minecraft.text.*
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

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
        val statFormatter = StatFormatter(stat)
        val label = run {
            val statName = statFormatter.name.build { color = config.colors.text.alt }
            Text.translatable("playerstatistics.command.leaderboard", statName).build { color = config.colors.text.main }
        }

        val leaderboard = Leaderboard.forStat(stat, highlightedName, page) ?: run {
            val content = label newLine Exceptions.NO_DATA.getMessage().build { color = config.colors.noData }
            val shareCode = storeShareData(label, content)
            return sendFeedback { content space Components.shareButton(shareCode) }
        }

        val entries = leaderboard.pageEntries.map { (pos, rank, player, value) ->
            literalText {
                val highlightedPlayer = player == highlightedName
                bold = highlightedPlayer
                color = config.colors.extra.altIf(highlightedPlayer)

                text(" » ") { bold = false }
                text("$rank. ") { color = config.colors.rank.altIf(highlightedPlayer) }
                text(player) {
                    var hint: Text? = null
                    var command: String? = null

                    if (highlightedPlayer) {
                        val targetPage = 1 + (pos - 2) / (Leaderboard.pageSize - 1)
                        // starting page               highlight on every page
                        // |                           |
                        // 1 + (pos - 2) / (pageSize - 1)
                        //            |
                        //            -1 starting index
                        //            -1 if highlight is at the end of a page, move it forward
                        if (targetPage != page) {
                            hint = Text.translatable("playerstatistics.command.leaderboard.jump")
                            command = PageCommand.formatCommand(targetPage)
                        }
                    } else {
                        hint = Text.translatable("playerstatistics.command.leaderboard.highlight")
                        command = formatCommand(statFormatter.commandArguments, player)
                    }

                    hoverEvent = hint?.let { HoverEvent(HoverEvent.Action.SHOW_TEXT, it) }
                    clickEvent = command?.let { ClickEvent(ClickEvent.Action.RUN_COMMAND, it) }
                    color = config.colors.name.altIf(highlightedPlayer)
                }
                text(" - ") { bold = false }
                text(statFormatter.formatValue(value)) { color = config.colors.value.altIf(highlightedPlayer) }
            }
        }

        val header = label space Components.posDisplay(page, leaderboard.pageCount) // TODO use pos/maxPos instead of pages
        val content = header newLine Texts.join(entries, literalText("\n"))
        val shareCode = storeShareData(label, content)

        sendFeedback { content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode) }
        registerPageAction(max = leaderboard.pageCount) { newPage -> sendLeaderboard(stat, highlightedName, newPage) }
    }

    fun formatStatNameWithSuggestion(statFormatter: StatFormatter<*>) = statFormatter.name.build {
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
        clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, formatCommand(statFormatter.commandArguments))
    }
}
