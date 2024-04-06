package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Database.Leaderboard
import net.minecraft.block.Block
import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

private object Colors {
    const val WHITE = 0xFFFFFF
    const val GRAY = 0xAAAAAA
    const val DARK_GRAY = 0x555555
}

private fun formatStatName(stat: String) = stat.split(':').map { Identifier.splitOn(it, '.') }.let { (statTypeId, statId) ->
    Registries.STAT_TYPE[statTypeId]?.let { statType ->
        statType.registry[statId]?.let { obj ->
            fun formatWithStatType(statText: Text) =
                Text.translatable("playerstatistics.stat_type.${statTypeId.toTranslationKey()}", statText)

            fun formatItemText(item: Item) = formatWithStatType(Text.empty().append(item.name).styled {
                it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackContent(item.defaultStack)))
            })

            when (obj) {
                is Block -> obj.asItem().takeIf { it != Items.AIR }?.let(::formatItemText) ?: formatWithStatType(obj.name)
                is Item -> formatItemText(obj)
                is EntityType<*> -> formatWithStatType(obj.name)
                is Identifier -> Text.translatable("stat.${statId.toTranslationKey()}")
                else -> null
            }
        }
    } ?: Text.literal(stat)
}

private fun MutableText.addPageFooter(page: Int, max: Int) = if (max <= 1) this else apply {
    val dashes = Text.literal("-".repeat(10)).withColor(Colors.GRAY)

    fun MutableText.styleButton(newPage: Int, translationKey: String, activated: Boolean): MutableText {
        return if (activated) withColor(Colors.WHITE).styled {
            it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(translationKey)))
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stats page $newPage"))
        } else withColor(Colors.GRAY)
    }

    append("\n")

    append(dashes)
    append(Text.literal(" ◀ [ ").styleButton(
        newPage = page - 1,
        translationKey = "playerstatistics.command.page.previous",
        activated = page > 1,
    ))

    append(Text.translatable("playerstatistics.command.page",
        Text.literal(page.toString()),
        Text.literal(max.toString()),
    ))

    append(Text.literal(" ] ▶ ").styleButton(
        newPage = page + 1,
        translationKey = "playerstatistics.command.page.next",
        activated = page < max,
    ))
    append(dashes)
}

suspend fun ServerCommandSource.sendLeaderboard(stat: String, page: Int = 1) {
    val leaderboard = Leaderboard.forStat(stat, player?.name?.string, page)

    val label = Text.translatable("playerstatistics.command.leaderboard", formatStatName(stat))
    val content = label.copy().apply {
        leaderboard.pageEntries.forEach { (rank, player, value) ->
            append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
            append("$rank. $player - $value")
        }
    }
    sendFeedback(content.copy().addPageFooter(page, leaderboard.totalPages))
    storeShareData(label, content)
    registerPageAction(max = leaderboard.totalPages) { sendLeaderboard(stat, it) }
}

suspend fun ServerCommandSource.sendServerTotal(stat: String) {
    val label = Text.translatable("playerstatistics.command.total", formatStatName(stat))
    val content = label.copy().apply {
        val total = Database.serverTotal(stat)
        append(": $total")
        player?.name?.string?.let { playerName ->
            Leaderboard.Entry.of(stat, playerName)?.takeIf { it.value > 0 }?.let { (_, _, value) ->
                append("\n")
                append(Text.translatable("playerstatistics.command.total.contributed", playerName, value, run {
                    val percentageFormat = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
                    percentageFormat.format(value.toFloat() / total * 100)
                }))
            }
        }
    }
    sendFeedback(content)
    storeShareData(label, content)
}

suspend fun ServerCommandSource.sendPlayerStat(stat: String, playerName: String) {
    Leaderboard.Entry.of(stat, playerName)?.let { (rank, player, value) ->
        val statText = formatStatName(stat)
        val label = Text.translatable("playerstatistics.command.player", player, statText)
        val content = Text.literal("$player: $value ").apply {
            append(statText)
            if (rank > 0) append(" (#$rank)")
        }
        sendFeedback(content)
        storeShareData(label, content)
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}

suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
    Leaderboard.forPlayer(playerName, page).takeIf { it.totalPages > 0 }?.let { leaderboard ->
        val label = Text.translatable("playerstatistics.command.top", Database.fixPlayerName(playerName))
        val content = label.copy().apply {
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                append(Text.literal("\n» ").withColor(Colors.DARK_GRAY))
                append("#$rank ")
                append(formatStatName(stat))
                append(" - $value")
            }
        }
        sendFeedback(content.copy().addPageFooter(page, leaderboard.totalPages))
        storeShareData(label, content)
        registerPageAction(max = leaderboard.totalPages) { sendPlayerTopStats(playerName, it) }
    } ?: sendError(Text.translatable("playerstatistics.argument.player.unknown", playerName))
}
