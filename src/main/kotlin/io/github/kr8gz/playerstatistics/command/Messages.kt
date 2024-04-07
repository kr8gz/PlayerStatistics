package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Database.Leaderboard
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.sendFeedback
import io.github.kr8gz.playerstatistics.messages.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.text.ClickEvent
import net.minecraft.text.Text

suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, page: Int = 1) {
    val leaderboard = Leaderboard.forStat(stat, player?.name?.string, page)

    val label = Text.translatable("playerstatistics.command.leaderboard", stat.formatName())
    val content = label.copy().apply {
        leaderboard.pageEntries.forEach { (rank, player, value) ->
            append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
            append("$rank. $player - ")
            append(stat.formatValue(value))
        }
    }
    val shareCode = storeShareData(label, content)
    sendFeedback { content.copy().append(Components.pageFooter(page, leaderboard.pageCount, shareCode)) }
    registerPageAction(max = leaderboard.pageCount) { sendLeaderboard(stat, it) }
}

suspend fun ServerCommandSource.sendServerTotal(stat: Stat<*>) {
    val label = Text.translatable("playerstatistics.command.total", stat.formatName())
    val content = label.copy().apply {
        val total = Database.serverTotal(stat)
        append(": $total")
        player?.name?.string?.let { playerName ->
            Leaderboard.Entry.of(stat, playerName)?.takeIf { it.value > 0 }?.let { (_, _, value) ->
                append("\n")
                val percentage = formatDecimal(value.toFloat() / total * 100)
                append(Text.translatable("playerstatistics.command.total.contributed", playerName, stat.formatValue(value), percentage))
            }
        }
    }
    val shareCode = storeShareData(label, content)
    sendFeedback { content.copy().append(Components.shareButton(shareCode)) }
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
                append(" (")
                append(Text.translatable("playerstatistics.command.player.rank", rank).styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}"))
                })
                append(")")
            }
        }
        val shareCode = storeShareData(label, content)
        sendFeedback { content.copy().append(Components.shareButton(shareCode)) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}

suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
    Leaderboard.forPlayer(playerName, page).takeIf { it.pageCount > 0 }?.let { leaderboard ->
        val label = Text.translatable("playerstatistics.command.top", Database.fixPlayerName(playerName))
        val content = label.copy().apply {
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
                append(Text.translatable("playerstatistics.command.player.rank", rank))
                append(" ")
                append(stat.formatName().styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}"))
                })
                append(" - ")
                append(stat.formatValue(value))
            }
        }
        val shareCode = storeShareData(label, content)
        sendFeedback { content.copy().append(Components.pageFooter(page, leaderboard.pageCount, shareCode)) }
        registerPageAction(max = leaderboard.pageCount) { sendPlayerTopStats(playerName, it) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}
