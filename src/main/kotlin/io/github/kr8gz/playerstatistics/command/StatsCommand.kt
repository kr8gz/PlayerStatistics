package io.github.kr8gz.playerstatistics.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.kr8gz.playerstatistics.database.Database
import kotlinx.coroutines.launch
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
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
    private object Arguments {
        const val STAT_TYPE = "type"
        const val STAT = "stat"
        const val PLAYER = "player"
        const val PAGE = "page"
    }

    private object Exceptions {
        val DATABASE_INITIALIZING = SimpleCommandExceptionType(
            Text.translatable("playerstatistics.database.initializing")
        )
        val UNKNOWN_STATISTIC = DynamicCommandExceptionType { name ->
            Text.stringifiedTranslatable("playerstatistics.argument.statistic.unknown", name)
        }
    }

    private companion object {
        fun getStatName(statType: RegistryKey<StatType<*>>, identifier: Identifier): String {
            fun Identifier.adjustFormat() = this.toString().replace(':', '.')
            return statType.value.adjustFormat() + ":" + identifier.adjustFormat()
        }

        val ALL_STATS = Registries.STAT_TYPE.entrySet.flatMap { (key, type) ->
            type.registry.ids.mapNotNull { value -> getStatName(key, value) }
        }
    }

    private inline fun useDatabase(context: ServerCommandContext, crossinline command: suspend Database.() -> Unit): Int {
        if (Database.Initializer.inProgress) throw Exceptions.DATABASE_INITIALIZING.create()
        Database.launch {
            context.source.server.playerManager.playerList.forEach {
                Database.Updater.updateStats(it.statHandler)
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
                    throw Exceptions.UNKNOWN_STATISTIC.create(statName)
                }
            }
        }

        return this
            .then(literal("random").executes { context -> command(ALL_STATS.random(), context) })
            .then(argument(Arguments.STAT_TYPE, RegistryEntryArgumentType.registryEntry(registryAccess, RegistryKeys.STAT_TYPE))
                // hide the minecraft:custom stat category so that custom stats are easier to find (see last branch)
                .suggests { _, builder ->
                    val types = Registries.STAT_TYPE.streamEntries()
                        .filter { entry -> entry.value() != Stats.CUSTOM }
                        .map { entry -> entry.registryKey().value }
                    CommandSource.suggestIdentifiers(types, builder)
                }
                .then(argument(Arguments.STAT, IdentifierArgumentType.identifier())
                    .suggests { context, builder ->
                        val statType = RegistryEntryArgumentType.getRegistryEntry(context, Arguments.STAT_TYPE, RegistryKeys.STAT_TYPE).value()
                        CommandSource.suggestIdentifiers(statType.registry.ids, builder)
                    }
                    .executesWithCheckedStatName { context ->
                        val type = RegistryEntryArgumentType.getRegistryEntry(context, Arguments.STAT_TYPE, RegistryKeys.STAT_TYPE)
                        val id = IdentifierArgumentType.getIdentifier(context, Arguments.STAT)
                        getStatName(type.registryKey(), id)
                    }
                )
            )
            // merge custom stats with stat type argument
            .then(argument(Arguments.STAT, RegistryEntryArgumentType.registryEntry(registryAccess, RegistryKeys.CUSTOM_STAT))
                .executesWithCheckedStatName { context ->
                    val id = RegistryEntryArgumentType.getRegistryEntry(context, Arguments.STAT, RegistryKeys.CUSTOM_STAT)
                    getStatName(Registries.STAT_TYPE.getKey(Stats.CUSTOM).get(), id.value())
                }
            )
    }

    init {
        dispatcher.register(literal("stats")
            .then(literal("leaderboard").executesWithStatArgument { stat, context ->
                useDatabase(context) { context.source.sendLeaderboard(stat, context.source.player?.name?.string) }
            })
            .then(literal("total").executesWithStatArgument { stat, context ->
                useDatabase(context) { context.source.sendServerTotal(stat, context.source.player?.name?.string) }
            })
            .then(literal("player")
                .then(argument(Arguments.PLAYER, StringArgumentType.word())
                    .suggests { _, builder -> CommandSource.suggestMatching(Database.playerNames, builder) }
                    .executesWithStatArgument { stat, context ->
                        val player = StringArgumentType.getString(context, Arguments.PLAYER)
                        useDatabase(context) { context.source.sendPlayerStat(stat, player) }
                    }
                )
            )
            .then(literal("top")
                .executes { context ->
                    val player = context.source.playerOrThrow.name.string
                    useDatabase(context) { context.source.sendPlayerTopStats(player) }
                }
                .then(argument(Arguments.PLAYER, StringArgumentType.word())
                    .suggests { _, builder -> CommandSource.suggestMatching(Database.playerNames, builder) }
                    .executes { context ->
                        val player = StringArgumentType.getString(context, Arguments.PLAYER)
                        useDatabase(context) { context.source.sendPlayerTopStats(player) }
                    }
                )
            )
            .then(literal("page")
                .then(argument(Arguments.PAGE, IntegerArgumentType.integer(1)).executes { context ->
                    useDatabase(context) {
                        // TODO
                    }
                })
            )
            .then(literal("share").executes { context ->
                useDatabase(context) {
                    // TODO share last Text output stored in map with player uuid as key
                }
            })
        )
    }
}
