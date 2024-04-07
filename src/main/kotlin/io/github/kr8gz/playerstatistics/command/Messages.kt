package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.MutableText.plus
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.messages.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.*

suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, page: Int = 1) {
    val leaderboard = Leaderboard.forStat(stat, player?.gameProfile?.name, page)

    val label = Text.translatable("playerstatistics.command.leaderboard", stat.formatName())
    val content = label.copy().apply {
        leaderboard.pageEntries.forEach { (rank, player, value) ->
            append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
            append("$rank. $player - ")
            append(stat.formatValue(value))
        }
    }
    val shareCode = storeShareData(label, content)
    sendFeedback { content.copy() + "\n" + Components.pageFooter(page, leaderboard.pageCount, shareCode) }
    registerPageAction(max = leaderboard.pageCount) { sendLeaderboard(stat, it) }
}

suspend fun ServerCommandSource.sendServerTotal(stat: Stat<*>) {
    val label = Text.translatable("playerstatistics.command.total", stat.formatName())
    val content = label.copy().apply {
        val total = Database.serverTotal(stat)
        append(": ")
        append(stat.formatValue(total))
        player?.let { player ->
            Leaderboard.Entry.of(stat, player.gameProfile.name)?.takeIf { it.value > 0 }?.let { (_, _, value) ->
                append("\n")
                append(Text.translatable("playerstatistics.command.total.contributed",
                    player.displayName,
                    stat.formatValue(value),
                    formatNumber(value.toFloat() / total * 100),
                ))
            }
        }
    }
    val shareCode = storeShareData(label, content)
    sendFeedback { content.copy() + " " + Components.shareButton(shareCode) }
}

suspend fun ServerCommandSource.sendPlayerStat(stat: Stat<*>, playerName: String) {
    Leaderboard.Entry.of(stat, playerName)?.let { (rank, player, value) ->
        val statText = stat.formatName()
        val label = Text.translatable("playerstatistics.command.player", player, statText)
        val content = Text.literal("$player: ").apply {
            append(stat.formatValue(value))
            append(" ")
            append(statText)
            if (rank > 0) {
                append(" ")
                append(Text.literal("(").apply {
                    append(Text.translatable("playerstatistics.command.player.rank", rank))
                    append(")")
                    styled {
                        it  .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint")))
                            .withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}"))
                    }
                })
            }
        }
        val shareCode = storeShareData(label, content)
        sendFeedback { content.copy() + " " + Components.shareButton(shareCode) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}

suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
    Leaderboard.forPlayer(playerName, page).takeIf { it.pageCount > 0 }?.let { leaderboard ->
        val label = Text.translatable("playerstatistics.command.top", Database.fixPlayerName(playerName))
        val content = label.copy().apply {
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
                append(Text.translatable("playerstatistics.command.player.rank", rank).apply {
                    append(" ")
                    append(stat.formatName())
                    styled {
                        it  .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.leaderboard.hint")))
                            .withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}"))
                    }
                })
                append(" - ")
                append(stat.formatValue(value))
            }
        }
        val shareCode = storeShareData(label, content)
        sendFeedback { content.copy() + "\n" + Components.pageFooter(page, leaderboard.pageCount, shareCode) }
        registerPageAction(max = leaderboard.pageCount) { sendPlayerTopStats(playerName, it) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}
