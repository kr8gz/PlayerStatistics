package io.github.kr8gz.playerstatistics.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.kr8gz.playerstatistics.database.Database
import kotlinx.coroutines.launch
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.RegistryEntryArgumentType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import java.util.*

typealias ServerCommandContext = CommandContext<ServerCommandSource>

class StatsCommand(
    dispatcher: CommandDispatcher<ServerCommandSource>,
    private val registryAccess: CommandRegistryAccess,
) {
    private object Arguments {
        const val STAT = "stat"
        const val PLAYER = "player"
        const val PAGE = "page"
    }

    private object Exceptions {
        val DATABASE_INITIALIZING = SimpleCommandExceptionType(
            Text.translatable("playerstatistics.database.initializing")
        )
        val ALREADY_RUNNING = SimpleCommandExceptionType(
            Text.translatable("playerstatistics.database.running")
        )
    }

    private val databaseUsers = HashSet<UUID?>()

    private inline fun useDatabase(context: ServerCommandContext, crossinline command: suspend Database.() -> Unit): Int {
        if (Database.Initializer.inProgress) throw Exceptions.DATABASE_INITIALIZING.create()
        if (!databaseUsers.add(context.source.uuid)) throw Exceptions.ALREADY_RUNNING.create()
        Database.launch {
            context.source.server.playerManager.playerList.forEach {
                Database.Updater.updateStats(it.statHandler)
            }
            Database.command()
            databaseUsers.remove(context.source.uuid)
        }
        return 0 // no meaningful immediate return value
    }

    private companion object {
        private fun <T> StatType<T>.getAll() = registry.map(::getOrCreateStat)
        val ALL_STATS = Registries.STAT_TYPE.flatMap { it.getAll() }
    }

    private fun <T : ArgumentBuilder<ServerCommandSource, T>> T.executesWithStatArgument(command: (context: ServerCommandContext, stat: Stat<*>) -> Int): T {
        fun <T : ArgumentBuilder<ServerCommandSource, T>, S> T.addArgumentsForStatType(statType: StatType<S>): T {
            @Suppress("UNCHECKED_CAST")
            val registryKey = statType.registry.key as RegistryKey<Registry<S>>
            return this.then(argument(Arguments.STAT, RegistryEntryArgumentType.registryEntry(registryAccess, registryKey)).executes { context ->
                val entry = RegistryEntryArgumentType.getRegistryEntry(context, Arguments.STAT, registryKey)
                command(context, statType.getOrCreateStat(entry.value()))
            })
        }

        Registries.STAT_TYPE.entrySet.forEach { (key, statType) ->
            // add custom ones directly to the outer level to make them easier to find
            if (statType == Stats.CUSTOM)
                this.addArgumentsForStatType(statType)
            else
                this.then(literal(key.value.path).addArgumentsForStatType(statType))
        }

        return this.then(literal("random").executes { context -> command(context, ALL_STATS.random()) })
    }

    init {
        dispatcher.register(literal("stats")
            .then(literal("leaderboard").executesWithStatArgument { context, stat ->
                useDatabase(context) { context.source.sendLeaderboard(stat) }
            })
            .then(literal("total").executesWithStatArgument { context, stat ->
                useDatabase(context) { context.source.sendServerTotal(stat) }
            })
            .then(literal("player")
                .then(argument(Arguments.PLAYER, StringArgumentType.word())
                    .suggests { _, builder -> CommandSource.suggestMatching(Database.playerNames, builder) }
                    .executesWithStatArgument { context, stat ->
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
                    val page = IntegerArgumentType.getInteger(context, Arguments.PAGE)
                    useDatabase(context) { context.source.runPageAction(page) }
                })
            )
            .then(literal("share").executes { context -> context.source.shareStoredData(); 0 })
        )
    }
}
