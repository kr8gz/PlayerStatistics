package io.github.kr8gz.playerstatistics.commands

import io.github.kr8gz.playerstatistics.commands.PageCommand.registerPageAction
import io.github.kr8gz.playerstatistics.commands.ShareCommand.storeShareData
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.Leaderboard
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.extensions.Text.build
import io.github.kr8gz.playerstatistics.extensions.Text.newLine
import io.github.kr8gz.playerstatistics.extensions.Text.space
import io.github.kr8gz.playerstatistics.format.Components
import io.github.kr8gz.playerstatistics.format.Components.withPageDisplay
import io.github.kr8gz.playerstatistics.util.ComputedStatSource
import io.github.kr8gz.playerstatistics.util.StatSource
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.*
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.core.text.literalText

object LeaderboardCommand : StatsCommand("leaderboard") {
    override fun LiteralCommandBuilder<ServerCommandSource>.build() {
        statArgument(ComputedStatSource.TOP_STATS) { maybeStat ->
            optionalPlayerArgument { maybePlayer ->
                executes {
                    val stat = maybeStat() ?: throw CommandExceptions.NO_DATA.create()
                    val player = maybePlayer() ?: source.player?.gameProfile?.name
                    usingDatabase { source.sendLeaderboard(stat, player) }
                }
            }
        }
    }

    private suspend fun ServerCommandSource.sendLeaderboard(stat: StatSource, highlightedName: String?, page: Int = 1) {
        val label = run {
            val statName = stat.formatNameText().build { color = config.colors.text.alt }
            Text.translatable("playerstatistics.command.leaderboard", statName).build { color = config.colors.text.main }
        }

        val leaderboard = Leaderboard.forStat(stat, highlightedName, page) ?: run {
            val content = label newLine CommandExceptions.NO_DATA.getMessage().build { color = config.colors.noData }
            val shareCode = storeShareData(label, content)
            return sendFeedback { content space Components.shareButton(shareCode) }
        }

        val highlightedPlayer = highlightedName?.let { Players.fixName(it) }

        val entries = leaderboard.pageEntries.map { (pos, rank, player, value) ->
            literalText {
                val isHighlighted = player == highlightedPlayer
                bold = isHighlighted
                color = config.colors.extra.altIf(isHighlighted)

                text(" » ") { bold = false }
                text("$rank. ") { color = config.colors.rank.altIf(isHighlighted) }
                text(player) {
                    var hint: Text? = null
                    var command: String? = null

                    if (isHighlighted) {
                        //         starting page           highlight on every page
                        //               |                                       |
                        val targetPage = 1 + (pos - 2) / (Leaderboard.pageSize - 1)
                        //                          |
                        //                          -1 start with index 0
                        //                          -1 if highlight is at the end of a page, move it forward
                        if (targetPage != page) {
                            hint = Text.translatable("playerstatistics.command.leaderboard.jump")
                            command = PageCommand.formatCommandString(targetPage)
                        }
                    } else {
                        hint = Text.translatable("playerstatistics.command.leaderboard.highlight")
                        command = LeaderboardCommand.formatCommandString(stat.formatCommandArgs(), player)
                    }

                    hoverEvent = hint?.let { HoverEvent(HoverEvent.Action.SHOW_TEXT, it) }
                    clickEvent = command?.let { ClickEvent(ClickEvent.Action.RUN_COMMAND, it) }
                    color = config.colors.name.altIf(isHighlighted)
                }
                text(" - ") { bold = false }
                text(stat.formatValueText(value)) { color = config.colors.value.altIf(isHighlighted) }
            }
        }

        val content = Texts.join(entries, literalText("\n"))
        val shareCode = storeShareData(label, label.withPageDisplay(page, leaderboard.pageCount) newLine content)

        sendFeedback { label newLine content newLine Components.pageFooter(page, leaderboard.pageCount, shareCode) }
        registerPageAction(max = leaderboard.pageCount) { newPage -> sendLeaderboard(stat, highlightedName, newPage) }
    }

    fun formatStatNameWithSuggestion(stat: StatSource) = stat.formatNameText().build {
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
        clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, formatCommandString(stat.formatCommandArgs()))
    }
}
