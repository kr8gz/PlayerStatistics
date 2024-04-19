package io.github.kr8gz.playerstatistics.command

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Players
import io.github.kr8gz.playerstatistics.database.Statistics
import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.RegistryEntryArgumentType
import net.minecraft.registry.Registries
import net.minecraft.resource.featuretoggle.ToggleableFeature
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.silkmc.silk.commands.ArgumentResolver
import net.silkmc.silk.commands.LiteralCommandBuilder
import net.silkmc.silk.commands.command
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private typealias CommandBuilder = net.silkmc.silk.commands.CommandBuilder<ServerCommandSource, *, *>
private typealias ArgumentBuilder<T> = CommandBuilder.(ArgumentResolver<ServerCommandSource, T>) -> Unit

abstract class StatsCommand(private val name: String) {
    companion object Root {
        private const val name = "stats"
        private val subcommands = listOf(
            LeaderboardCommand,
            ServerTotalCommand,
            PlayerStatCommand,
            PlayerTopStatsCommand,
            PageCommand,
            ShareCommand,
        )

        init {
            command(Root.name) {
                subcommands.forEach {
                    it.run {
                        literal(name) { build() }
                    }
                }
            }
        }
    }

    abstract fun LiteralCommandBuilder<ServerCommandSource>.build()

    protected enum class Exceptions(translationKey: String) {
        // Database
        DATABASE_INITIALIZING("playerstatistics.database.initializing"),
        ALREADY_RUNNING("playerstatistics.database.running"),
        // Sharing
        NO_SHARE_RESULTS("playerstatistics.command.share.nothing"),
        SHARE_UNAVAILABLE("playerstatistics.command.share.unavailable"),
        ALREADY_SHARED("playerstatistics.command.share.already_shared");

        private val exception = SimpleCommandExceptionType(Text.translatable(translationKey))
        fun create(): CommandSyntaxException = exception.create()
    }

    private val databaseUsers = ConcurrentHashMap.newKeySet<UUID>()

    protected fun CommandContext<ServerCommandSource>.usingDatabase(command: suspend () -> Unit) {
        if (Database.isInitializing) throw Exceptions.DATABASE_INITIALIZING.create()
        if (!databaseUsers.add(source.uuid)) throw Exceptions.ALREADY_RUNNING.create()

        Database.transaction {
            source.server.playerManager.playerList.forEach {
                Statistics.updateStats(it.statHandler)
            }
            command()
            databaseUsers.remove(source.uuid)
        }
    }

    protected fun CommandBuilder.statArgument(builder: ArgumentBuilder<Stat<*>>) {
        fun <T> CommandBuilder.addArgumentsForStatType(statType: StatType<T>, shortIds: Boolean = false) {
            val argumentType = { registryAccess: CommandRegistryAccess ->
                RegistryEntryArgumentType.registryEntry(registryAccess, statType.registry.key)
            }
            argument("stat", argumentType) { stat ->
                if (shortIds) suggests {
                    statType.registry.ids.map { it.toShortString() }
                }
                builder { statType.getOrCreateStat(stat().value()) }
            }
        }

        Registries.STAT_TYPE.entrySet.filter { it != Stats.CUSTOM }.sortedBy { it.key.value }.forEach { (key, statType) ->
            literal(key.value.path) {
                addArgumentsForStatType(statType)
            }
        }

        // add custom stats directly to the outer level to make them easier to find
        addArgumentsForStatType(Stats.CUSTOM, shortIds = true)

        literal("random") {
            builder {
                fun <T> StatType<T>.getAll() = registry
                    .filter { it !is ToggleableFeature || it.isEnabled(source.enabledFeatures) }
                    .map(::getOrCreateStat)

                Registries.STAT_TYPE.flatMap { it.getAll() }.random()
            }
        }
    }

    protected inline fun CommandBuilder.playerArgument(optional: Boolean = false, builder: ArgumentBuilder<String>) {
        argument<String>("player") { player ->
            suggests { Players.nameList }
            builder(player)
        }
        if (optional) builder { source.playerOrThrow.gameProfile.name }
    }

    fun format(vararg args: Any) = buildString {
        append("/${Root.name} $name")
        args.forEach { append(" "); append(it) }
    }
}
