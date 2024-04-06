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
                is Identifier -> Text.translatable("stat.${statId.toTranslationKey()}")
                else -> null
            }
        }
    } ?: Text.of(stat)
}

private fun ServerCommandSource.sendMessageAndStore(message: Text, pageAction: PageAction = null) {
    sendMessage(message)
    player?.let {
        OutputManager.storeOutput(it.uuid, message)
        OutputManager.registerPageAction(it.uuid, pageAction)
    }
}

suspend fun ServerCommandSource.sendLeaderboard(stat: String, highlightName: String?, page: Int = 1) {
    Text.literal("TODO header").apply {
        Database.Leaderboard.forStat(stat, highlightName, page).pageEntries
            .forEach { (rank, player, value) -> append("\n$rank. $player - $value") }
    }.let { sendMessageAndStore(it) { page -> sendLeaderboard(stat, highlightName, page) } }
}

suspend fun ServerCommandSource.sendServerTotal(stat: String, highlightName: String?) {
    val total = Database.serverTotal(stat)
    Text.translatable("playerstatistics.command.total", formatStatName(stat), total).apply {
        highlightName?.let { name ->
            Database.Leaderboard.Entry.of(stat, name)?.takeIf { it.value > 0 }?.let { (_, player, value) ->
                append("\n")
                val percentage = value.toFloat() / total * 100
                append(Text.translatable("playerstatistics.command.total.contributed", player, value, percentage))
            }
        }
    }.let(::sendMessageAndStore)
}

suspend fun ServerCommandSource.sendPlayerStat(stat: String, playerName: String) {
    Database.Leaderboard.Entry.of(stat, playerName)?.let { (rank, player, value) ->
        Text.literal("$player: $value ").apply {
            append(formatStatName(stat))
            if (rank > 0) append(" (#$rank)")
        }.let(::sendMessageAndStore)
    } ?: sendError(formatUnknownPlayerError(playerName))
}

suspend fun ServerCommandSource.sendPlayerTopStats(playerName: String, page: Int = 1) {
    Database.Leaderboard.forPlayer(playerName, page).takeIf { it.totalPages > 0 }?.let { leaderboard ->
        Text.literal("TODO header").apply {
            leaderboard.pageEntries.forEach { (rank, stat, value) ->
                append("\n#$rank ")
                append(formatStatName(stat))
                append(" - $value")
            }
        }.let { sendMessageAndStore(it) { page -> sendPlayerTopStats(playerName, page) } }
    } ?: sendError(formatUnknownPlayerError(playerName))
}
