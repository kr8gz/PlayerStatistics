package io.github.kr8gz.playerstatistics.commands

import com.mojang.brigadier.context.CommandContext
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import io.github.kr8gz.playerstatistics.util.MinecraftStatSource
import io.github.kr8gz.playerstatistics.util.StatSource
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.silkmc.silk.commands.*
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

    protected abstract fun LiteralCommandBuilder<ServerCommandSource>.build()

    private val databaseUsers = ConcurrentHashMap.newKeySet<UUID>()

    protected fun CommandContext<ServerCommandSource>.usingDatabase(command: suspend () -> Unit) {
        if (!Database.Initializer.isCompleted) throw CommandExceptions.DATABASE_INITIALIZING.create()
        if (!databaseUsers.add(source.uuid)) throw CommandExceptions.ALREADY_RUNNING.create()

        Database.transaction {
            source.server.playerManager.playerList.forEach {
                Statistics.updateStats(it.statHandler)
            }
            try { command() } finally { databaseUsers.remove(source.uuid) }
        }
    }

    protected fun CommandBuilder.statArgument(vararg additionalSources: StatSource, builder: ArgumentBuilder<StatSource?>) {
        fun <T> CommandBuilder.addOptionsForType(statType: StatType<T>) {
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
                        Stats.CUSTOM.registry.ids.map { it.toShortString() }
                    }
                }
                builder { MinecraftStatSource(statType.getOrCreateStat(stat().value())) }
            }
        }

        addOptionsForType(Stats.CUSTOM) // add custom stats directly to the outer level to make them easier to find

        val statTypes = Registries.STAT_TYPE.entrySet
            .filter { (_, type) -> type != Stats.CUSTOM } // don't add them again
            .sortedBy { it.key.value }

        statTypes.forEach { (key, statType) ->
            literal(key.value.toShortString()) {
                addOptionsForType(statType)
            }
        }

        literal("random") {
            builder { Statistics.storedStats.randomOrNull()?.let(::MinecraftStatSource) }
        }

        for (source in additionalSources) {
            literal(source.formatCommandArgs()) {
                builder { source }
            }
        }
    }

    protected fun CommandBuilder.optionalPlayerArgument(builder: ArgumentBuilder<String?>) {
        argument<String>("player") { player ->
            suggests { Players.nameList }
            builder(player)
        }
        builder { null }
    }

    fun formatCommandString(vararg args: Any) = buildString {
        append("/${Root.name} $name")
        args.forEach { append(" $it") }
    }
}
