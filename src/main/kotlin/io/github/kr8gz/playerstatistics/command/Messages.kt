package io.github.kr8gz.playerstatistics.command

import io.github.kr8gz.playerstatistics.database.Database
import net.minecraft.block.Block
import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

private fun formatUnknownPlayerError(name: String): Text =
    Text.translatable("playerstatistics.argument.player.unknown", name).formatted(Formatting.RED)

private fun formatStatName(stat: String): Text {
    val (statTypeId, statId) = stat.split(':').map { Identifier.splitOn(it, '.') }

    return Registries.STAT_TYPE.get(statTypeId)?.let { statType ->
        statType.registry.get(statId)?.let {
            fun formatWithStatType(statName: Text) =
                Text.translatable("playerstatistics.stat_type.${statTypeId.toTranslationKey()}", statName)

            when (it) {
                is Block -> formatWithStatType(it.name)
                is Item -> formatWithStatType(it.name)
                is EntityType<*> -> formatWithStatType(it.name)
                else -> Text.translatable("stat.${statId.toTranslationKey()}")
            }
        }
    } ?: Text.of(stat)
}

suspend fun ServerCommandSource.sendLeaderboard(stat: String, highlightName: String?, page: Int = 1) {
    Text.literal("TODO header").apply {
        Database.Leaderboard.forStat(stat, highlightName, page).forEach { entry ->
            append("\n${entry.rank}. ${entry.key} - ${entry.value}")
        }
    }.let(::sendMessage)
}

suspend fun ServerCommandSource.sendServerTotal(stat: String, highlightName: String?) {
    val total = Database.serverTotal(stat)
    Text.translatable("playerstatistics.command.total", formatStatName(stat), total).apply {
        highlightName?.let { name ->
            Database.statForPlayer(stat, name)?.takeIf { it.rank > 0 }?.let { entry ->
                append("\n")
                val percentage = entry.value.toFloat() / total * 100
                append(Text.translatable("playerstatistics.command.total.contributed", entry.value, percentage))
            }
        }
    }.let(::sendMessage)
}

suspend fun ServerCommandSource.sendPlayerStat(stat: String, playerName: String) {
    Database.statForPlayer(stat, playerName)?.let { entry ->
        Text.literal("${entry.key}: ${entry.value} ").apply {
            append(formatStatName(stat))
            if (entry.rank > 0) {
                append(" ")
                append(Text.translatable("playerstatistics.command.player.rank", entry.rank))
            }
        }.let(::sendMessage)
    } ?: sendError(formatUnknownPlayerError(playerName))
}

suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
    Database.Leaderboard.forPlayer(playerName, page).takeIf { it.isNotEmpty() }?.let { leaderboard ->
        Text.literal("TODO header").apply {
            leaderboard.forEach { entry ->
                append("\n${entry.rank}. ")
                append(formatStatName(entry.key))
                append(" - ${entry.value}")
            }
        }.let(::sendMessage)
    } ?: sendError(formatUnknownPlayerError(playerName))
}
