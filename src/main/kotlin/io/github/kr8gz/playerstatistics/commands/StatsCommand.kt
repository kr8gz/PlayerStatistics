package io.github.kr8gz.playerstatistics.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Database.Updater
import kotlinx.coroutines.launch
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.command.argument.RegistryEntryArgumentType
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.minecraft.util.Identifier

typealias ServerCommandContext = CommandContext<ServerCommandSource>

class StatsCommand(
    dispatcher: CommandDispatcher<ServerCommandSource>,
    private val registryAccess: CommandRegistryAccess,
) {
    private companion object {
        const val STAT_TYPE_ARGUMENT = "type"
        const val STAT_ARGUMENT = "stat"
        const val PLAYER_ARGUMENT = "player"

        fun getStatName(statType: RegistryKey<StatType<*>>, identifier: Identifier): String {
            fun Identifier.adjustFormat() = this.toString().replace(':', '.')
            return statType.value.adjustFormat() + ":" + identifier.adjustFormat()
        }

        val ALL_STATS = Registries.STAT_TYPE.entrySet.flatMap { (key, type) ->
            type.registry.ids.mapNotNull { value -> getStatName(key, value) }
        }

        val INVALID_CRITERION_EXCEPTION = DynamicCommandExceptionType { name ->
            Text.stringifiedTranslatable("playerstatistics.argument.statistic.unknown", name)
        }

        val DATABASE_INITIALIZING_EXCEPTION = SimpleCommandExceptionType(
            Text.translatable("playerstatistics.database.exception.initializing")
        )
    }

    private inline fun useDatabase(context: ServerCommandContext, crossinline command: suspend Database.() -> Unit): Int {
        if (Database.Initializer.inProgress) throw DATABASE_INITIALIZING_EXCEPTION.create()
        Database.launch {
            context.source.server.playerManager.playerList.forEach {
                Updater.updateStats(it.statHandler)
            }
            Database.command()
        }
        return 0 // no meaningful immediate return value
    }

    private fun <T : ArgumentBuilder<ServerCommandSource, T>> T.executesWithStatArgument(command: (String, ServerCommandContext) -> Int): T {
        fun <T : ArgumentBuilder<ServerCommandSource, T>> T.executesWithCheckedStatName(statNameSupplier: (ServerCommandContext) -> String): T {
            return this.executes { context ->
                val statName = statNameSupplier(context)
                if (statName in ALL_STATS) {
                    command(statName, context)
                } else {
                    throw INVALID_CRITERION_EXCEPTION.create(statName)
                }
            }
        }

        return this
            .then(literal("random").executes { context -> command(ALL_STATS.random(), context) })
            .then(argument(STAT_TYPE_ARGUMENT, RegistryEntryArgumentType.registryEntry(registryAccess, RegistryKeys.STAT_TYPE))
                // hide the minecraft:custom stat category so that custom stats are easier to find (see last branch)
                .suggests { _, builder ->
                    val types = Registries.STAT_TYPE.streamEntries()
                        .filter { entry -> entry.value() != Stats.CUSTOM }
                        .map { entry -> entry.registryKey().value }
                    CommandSource.suggestIdentifiers(types, builder)
                }
                .then(argument(STAT_ARGUMENT, IdentifierArgumentType.identifier())
                    .suggests { context, builder ->
                        val statType = RegistryEntryArgumentType.getRegistryEntry(context, STAT_TYPE_ARGUMENT, RegistryKeys.STAT_TYPE).value()
                        CommandSource.suggestIdentifiers(statType.registry.ids, builder)
                    }
                    .executesWithCheckedStatName { context ->
                        val type = RegistryEntryArgumentType.getRegistryEntry(context, STAT_TYPE_ARGUMENT, RegistryKeys.STAT_TYPE)
                        val id = IdentifierArgumentType.getIdentifier(context, STAT_ARGUMENT)
                        getStatName(type.registryKey(), id)
                    }
                )
            )
            // merge custom stats with stat type argument
            .then(argument(STAT_ARGUMENT, RegistryEntryArgumentType.registryEntry(registryAccess, RegistryKeys.CUSTOM_STAT))
                .executesWithCheckedStatName { context ->
                    val id = RegistryEntryArgumentType.getRegistryEntry(context, STAT_ARGUMENT, RegistryKeys.CUSTOM_STAT)
                    getStatName(Registries.STAT_TYPE.getKey(Stats.CUSTOM).get(), id.value())
                }
            )
    }

    init {
        dispatcher.register(literal("stats")
            .then(literal("leaderboard").executesWithStatArgument { stat, context ->
                useDatabase(context) {
                    val leaderboard = Database.Leaderboard.forStat(stat, context.source.player)
                    context.source.sendMessage(Text.of(leaderboard.joinToString("\n")))
                }
            })
            .then(literal("total").executesWithStatArgument { stat, context ->
                useDatabase(context) {
                    val total = serverTotal(stat)
                    val contributed = context.source.player?.let { statForPlayer(stat, it.uuid) }
                    context.source.sendMessage(Text.of("$contributed/$total"))
                }
            })
            .then(literal("player")
                .then(argument(PLAYER_ARGUMENT, GameProfileArgumentType.gameProfile())
                    .executesWithStatArgument { stat, context ->
                        val players = GameProfileArgumentType.getProfileArgument(context, PLAYER_ARGUMENT)
                        useDatabase(context) {
                            players.forEach { player ->
                                val value = statForPlayer(stat, player.id)
                                context.source.sendMessage(Text.of(value.toString()))
                            }
                        }
                    }
                )
            )
            .then(literal("top")
                // TODO optional player argument -> context.source.getPlayerOrThrow()
                .then(argument(PLAYER_ARGUMENT, GameProfileArgumentType.gameProfile()).executes { context ->
                    val players = GameProfileArgumentType.getProfileArgument(context, PLAYER_ARGUMENT)
                    useDatabase(context) {
                        players.forEach {
                            context.source.sendMessage(Text.of("${it.name}'s top statistics"))
                        }
                    }
                })
            )
            // /stats page <page>
            // /stats share <code>
        )
    }
}
