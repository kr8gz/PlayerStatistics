package io.github.kr8gz.playerstatistics.commands

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandExceptionType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.silkmc.silk.commands.ArgumentResolver
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.commands.command
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

//? if <1.20.5 {
/*import net.minecraft.command.argument.RegistryEntryArgumentType
*///?} else
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType as RegistryEntryArgumentType

private typealias CommandBuilder = net.silkmc.silk.commands.CommandBuilder<ServerCommandSource, *, *>
private typealias ArgumentBuilder<T> = CommandBuilder.(ArgumentResolver<ServerCommandSource, T>) -> Unit

abstract class StatsCommand(private val name: String) {
    companion object Root {
        private const val name = "stats"
        private val subcommands = listOf(
            LeaderboardCommand,
            ServerTotalCommand,
            PlayerTopStatsCommand,
            PageCommand,
            ShareCommand,
        )

        init {
            command(Root.name) {
                for (command in subcommands) command.run {
                    literal(name) { build() }
                }
            }
        }
    }

    abstract fun LiteralCommandBuilder<ServerCommandSource>.build()

    protected enum class Exceptions(private val translationKey: String) : CommandExceptionType {
        NO_DATA("playerstatistics.no_data"),
        UNKNOWN_PLAYER("playerstatistics.argument.player.unknown"),

        // Database
        DATABASE_INITIALIZING("playerstatistics.database.initializing"),
        ALREADY_RUNNING("playerstatistics.database.running"),

        // Sharing
        SHARE_UNAVAILABLE("playerstatistics.command.share.unavailable"),
        ALREADY_SHARED("playerstatistics.command.share.already_shared");

        fun getMessage(vararg args: Any?): Text = Text.translatable(translationKey, *args)
        fun create(vararg args: Any?) = CommandSyntaxException(this, getMessage(*args))
    }

    private val databaseUsers = ConcurrentHashMap.newKeySet<UUID>()

    protected fun CommandContext<ServerCommandSource>.usingDatabase(command: suspend () -> Unit) {
        if (!Database.Initializer.isCompleted) throw Exceptions.DATABASE_INITIALIZING.create()
        if (!databaseUsers.add(source.uuid)) throw Exceptions.ALREADY_RUNNING.create()

        Database.transaction {
            source.server.playerManager.playerList.forEach {
                Statistics.updateStats(it.statHandler)
            }
            try { command() } finally { databaseUsers.remove(source.uuid) }
        }
    }

    protected fun CommandBuilder.statArgument(builder: ArgumentBuilder<Stat<*>?>) {
        fun <T> CommandBuilder.addArgumentsForStatType(statType: StatType<T>) {
            val argumentType = { registryAccess: CommandRegistryAccess ->
                RegistryEntryArgumentType.registryEntry(registryAccess, statType.registry.key)
            }
            argument("stat", argumentType) { stat ->
                when (statType) {
                    Stats.BROKEN -> suggestsIdentifiers {
                        Stats.BROKEN.registry.entrySet.filter {
                            /*? if <1.20.5 {*/ /*it.value.isDamageable *///?} else
                            it.value.components.contains(net.minecraft.component.DataComponentTypes.DAMAGE)
                        }.map { it.key.value }
                    }
                    Stats.CUSTOM -> suggests {
                        statType.registry.ids.map { it.toShortString() }
                    }
                }
                builder { statType.getOrCreateStat(stat().value()) }
            }
        }

        Registries.STAT_TYPE.entrySet.sortedBy { it.key.value }.forEach { (key, statType) ->
            when (statType) {
                Stats.CUSTOM -> addArgumentsForStatType(statType) // add custom stats directly to the outer level to make them easier to find
                else -> literal(key.value.toShortString()) { addArgumentsForStatType(statType) }
            }
        }

        literal("random") {
            builder { Statistics.statList.randomOrNull() }
        }
    }

    protected inline fun CommandBuilder.optionalPlayerArgument(builder: ArgumentBuilder<String?>) {
        argument<String>("player") { player ->
            suggests { Players.nameList }
            builder(player)
        }
        builder { null }
    }

    fun formatCommand(vararg args: Any) = buildString {
        append("/${Root.name} $name")
        args.forEach { append(" $it") }
    }
}
