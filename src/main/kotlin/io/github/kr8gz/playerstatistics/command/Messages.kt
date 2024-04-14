package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.MutableText.plus
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.messages.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.silkmc.silk.core.text.literalText

suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, page: Int = 1) {
    val leaderboard = Leaderboard.forStat(stat, player?.gameProfile?.name, page)

    val label = Text.translatable("playerstatistics.command.leaderboard", stat.formatName())
    val content = literalText {
        text(label)
        leaderboard.pageEntries.forEach { (rank, player, value) ->
            text("\n» ") { color = Colors.DARK_GRAY }
            text("$rank. $player - ")
            text(stat.formatValue(value))
        }
    }
    sendFeedback {
        val shareCode = storeShareData(label, content)
        content.copy() + "\n" + Components.pageFooter(page, leaderboard.pageCount, shareCode)
    }
    registerPageAction(max = leaderboard.pageCount) { sendLeaderboard(stat, it) }
}

suspend fun ServerCommandSource.sendServerTotal(stat: Stat<*>) {
    val total = Database.serverTotal(stat)

    val label = Text.translatable("playerstatistics.command.total", stat.formatName())
    val content = literalText {
        text(label)
        text(": ")
        text(stat.formatValue(total))
        player?.let { player ->
            Leaderboard.Entry.of(stat, player.gameProfile.name)?.takeIf { it.value > 0 }?.let { (_, _, value) ->
                text("\n")
                text(Text.translatable("playerstatistics.command.total.contributed",
                    player.displayName,
                    stat.formatValue(value),
                    formatNumber(value.toFloat() / total * 100),
                ))
            }
        }
    }
    sendFeedback {
        val shareCode = storeShareData(label, content)
        content.copy() + " " + Components.shareButton(shareCode)
    }
}

suspend fun ServerCommandSource.sendPlayerStat(stat: Stat<*>, playerName: String) {
    Leaderboard.Entry.of(stat, playerName)?.let { (rank, player, value) ->
        val statText = stat.formatName()

        val label = Text.translatable("playerstatistics.command.player", player, statText)
        val content = literalText("$player: ") {
            text(stat.formatValue(value))
            text(" ")
            text(statText)
            if (rank > 0) {
                text(" ") {
                    text("(")
                    text(Text.translatable("playerstatistics.command.player.rank", rank))
                    text(")")
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
                    clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}")
                }
            }
        }
        sendFeedback {
            val shareCode = storeShareData(label, content)
            content.copy() + " " + Components.shareButton(shareCode)
        }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}

suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
    Leaderboard.forPlayer(playerName, page).takeIf { it.pageCount > 0 }?.let { leaderboard ->
        val label = Text.translatable("playerstatistics.command.top", Database.fixPlayerName(playerName))
        val content = literalText {
            text(label)
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                text("\n» ") { color = Colors.DARK_GRAY }
                text(Text.translatable("playerstatistics.command.player.rank", rank)) {
                    text(" ")
                    text(stat.formatName())
                    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint"))
                    clickEvent = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}")
                }
                text(" - ")
                text(stat.formatValue(value))
            }
        }
        sendFeedback {
            val shareCode = storeShareData(label, content)
            content.copy() + "\n" + Components.pageFooter(page, leaderboard.pageCount, shareCode)
        }
        registerPageAction(max = leaderboard.pageCount) { sendPlayerTopStats(playerName, it) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}
