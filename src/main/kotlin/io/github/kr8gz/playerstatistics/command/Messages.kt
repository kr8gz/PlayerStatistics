package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Database.Leaderboard
import net.minecraft.block.Block
import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.Stats
import net.minecraft.text.*
import net.minecraft.util.Identifier
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

private object Colors {
    const val WHITE = 0xFFFFFF
    const val GRAY = 0xAAAAAA
    const val DARK_GRAY = 0x555555
}

private fun Stat<*>.formatName(): MutableText {
    fun formatWithStatType(statText: Text): MutableText {
        return Text.translatable("playerstatistics.stat_type.${type.identifier.toTranslationKey()}", statText)
    }

    fun formatItemText(item: Item) = formatWithStatType(Text.empty().append(item.name).styled {
        it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(item.defaultStack)))
    })

    return when (val obj = value) {
        is Block -> obj.asItem().takeIf { it != Items.AIR }?.let(::formatItemText) ?: formatWithStatType(obj.name)
        is Item -> formatItemText(obj)
        is EntityType<*> -> formatWithStatType(obj.name)
        is Identifier -> Text.translatable("stat.${obj.toTranslationKey()}")
        else -> Text.literal(name)
    }
}

private fun <T> Stat<T>.asCommandArguments(): String {
    val statId = type.registry.getKey(value).get().value
    return if (type == Stats.CUSTOM) statId.toShortString() else "${type.identifier.path} $statId"
}

private fun MutableText.addPageFooter(page: Int, max: Int) = apply {
    fun MutableText.styleButton(newPage: Int, translationKey: String, active: Boolean): MutableText {
        return if (active) withColor(Colors.WHITE).styled {
            it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(translationKey)))
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats page $newPage"))
        } else withColor(Colors.GRAY)
    }

    append("\n")
    append(Text.literal("-----").withColor(Colors.GRAY))
    append(Text.literal(" ◀ [ ").styleButton(
        newPage = page - 1,
        translationKey = "playerstatistics.command.page.previous",
        active = page > 1,
    ))
    append(Text.translatable("playerstatistics.command.page",
        Text.literal(page.coerceAtMost(max).toString()),
        Text.literal(max.toString()),
    ))
    append(Text.literal(" ] ▶ ").styleButton(
        newPage = page + 1,
        translationKey = "playerstatistics.command.page.next",
        active = page < max,
    ))
    append(Text.literal("--").withColor(Colors.GRAY))
    addShareButton()
    append(" ")
    append(Text.literal("-----").withColor(Colors.GRAY))
}

private fun MutableText.addShareButton() = apply {
    append(" ")
    append(Texts.bracketed(Text.translatable("playerstatistics.command.share")).styled {
        it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("playerstatistics.command.share.hint")))
            .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats share"))
    })
}

suspend fun ServerCommandSource.sendLeaderboard(stat: Stat<*>, page: Int = 1) {
    val leaderboard = Leaderboard.forStat(stat, player?.name?.string, page)

    val label = Text.translatable("playerstatistics.command.leaderboard", stat.formatName())
    val content = label.copy().apply {
        leaderboard.pageEntries.forEach { (rank, player, value) ->
            append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
            append("$rank. $player - ${stat.format(value)}")
        }
    }
    sendFeedback(content.copy().addPageFooter(page, leaderboard.pageCount))
    storeShareData(label, content)
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
                append(Text.translatable("playerstatistics.command.total.contributed", playerName, stat.format(value), run {
                    val percentageFormat = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
                    percentageFormat.format(value.toFloat() / total * 100)
                }))
            }
        }
    }
    sendFeedback(content.copy().addShareButton())
    storeShareData(label, content)
}

suspend fun ServerCommandSource.sendPlayerStat(stat: Stat<*>, playerName: String) {
    Leaderboard.Entry.of(stat, playerName)?.let { (rank, player, value) ->
        val statText = stat.formatName()
        val label = Text.translatable("playerstatistics.command.player", player, statText)
        val content = Text.literal("$player: ${stat.format(value)} ").apply {
            append(statText)
            if (rank > 0) {
                append(" (")
                append(Text.translatable("playerstatistics.command.player.rank", rank).styled {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stats leaderboard ${stat.asCommandArguments()}"))
                })
                append(")")
            }
        }
        sendFeedback(content.copy().addShareButton())
        storeShareData(label, content)
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
                append(" - ${stat.format(value)}")
            }
        }
        sendFeedback(content.copy().addPageFooter(page, leaderboard.pageCount))
        storeShareData(label, content)
        registerPageAction(max = leaderboard.pageCount) { sendPlayerTopStats(playerName, it) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}
