package io.github.kr8gz.playerstatistics.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.github.kr8gz.playerstatistics.PlayerStatistics
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.extensions.Identifier.toShortString
import io.github.kr8gz.playerstatistics.extensions.ServerCommandSource.uuid
import kotlinx.coroutines.launch
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.RegistryEntryArgumentType
import net.minecraft.command.argument.UuidArgumentType
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.resource.featuretoggle.ToggleableFeature
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class StatsCommand(
    dispatcher: CommandDispatcher<ServerCommandSource>,
    private val registryAccess: CommandRegistryAccess,
) {
    private object Arguments {
        const val STAT = "stat"
        const val PLAYER = "player"
        const val PAGE = "page"
        const val CODE = "code"
    }

    enum class Exceptions(translationKey: String) {
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

    private inline fun ServerCommandSource.useDatabase(crossinline command: suspend ServerCommandSource.() -> Unit): Int {
        if (Database.Initializer.inProgress) throw Exceptions.DATABASE_INITIALIZING.create()
        if (!databaseUsers.add(uuid)) throw Exceptions.ALREADY_RUNNING.create()

        Database.launch {
            server.playerManager.playerList.forEach { Database.Updater.updateStats(it.statHandler) }
            command()
            databaseUsers.remove(uuid)
        }
        return 0 // no meaningful immediate return value
    }

    private fun <T : ArgumentBuilder<ServerCommandSource, T>> T.executesWithStatArgument(command: (context: CommandContext<ServerCommandSource>, stat: Stat<*>) -> Int): T {
        fun <T : ArgumentBuilder<ServerCommandSource, T>, S> T.addArgumentsForStatType(statType: StatType<S>, shortIds: Boolean = false): T {
            @Suppress("UNCHECKED_CAST") // casting <out T> to <T> is safe for reading only
            val registryKey = statType.registry.key as RegistryKey<Registry<S>>
            val argument = argument(Arguments.STAT, RegistryEntryArgumentType.registryEntry(registryAccess, registryKey))

            if (shortIds) argument.suggests { _, builder ->
                CommandSource.suggestMatching(statType.registry.ids.map { it.toShortString() }, builder)
            }

            return this.then(argument.executes { context ->
                val entry = RegistryEntryArgumentType.getRegistryEntry(context, Arguments.STAT, registryKey)
                command(context, statType.getOrCreateStat(entry.value()))
            })
        }

        Registries.STAT_TYPE.entrySet.forEach { (key, statType) ->
            // add custom ones directly to the outer level to make them easier to find
            if (statType == Stats.CUSTOM)
                this.addArgumentsForStatType(statType, shortIds = true)
            else
                this.then(literal(key.value.path).addArgumentsForStatType(statType))
        }

        return this.then(literal("random").executes { context ->
            fun <T> StatType<T>.getAll() = registry
                .filter { it !is ToggleableFeature || it.isEnabled(context.source.enabledFeatures) }
                .map(::getOrCreateStat)

            command(context, Registries.STAT_TYPE.flatMap { it.getAll() }.random())
        })
    }

    init {
        dispatcher.register(literal("stats")
            .then(literal("leaderboard").executesWithStatArgument { context, stat ->
                context.source.useDatabase { sendLeaderboard(stat) }
            })
            .then(literal("total").executesWithStatArgument { context, stat ->
                context.source.useDatabase { sendServerTotal(stat) }
            })
            .then(literal("player")
                .then(argument(Arguments.PLAYER, StringArgumentType.word())
                    .suggests { _, builder -> CommandSource.suggestMatching(Database.playerNames, builder) }
                    .executesWithStatArgument { context, stat ->
                        val player = StringArgumentType.getString(context, Arguments.PLAYER)
                        context.source.useDatabase { sendPlayerStat(stat, player) }
                    }
                )
            )
            .then(literal("top")
                .executes { context ->
                    val player = context.source.playerOrThrow.gameProfile.name
                    context.source.useDatabase { sendPlayerTopStats(player) }
                }
                .then(argument(Arguments.PLAYER, StringArgumentType.word())
                    .suggests { _, builder -> CommandSource.suggestMatching(Database.playerNames, builder) }
                    .executes { context ->
                        val player = StringArgumentType.getString(context, Arguments.PLAYER)
                        context.source.useDatabase { sendPlayerTopStats(player) }
                    }
                )
            )
            .then(literal("page")
                .then(argument(Arguments.PAGE, IntegerArgumentType.integer(1)).executes { context ->
                    val page = IntegerArgumentType.getInteger(context, Arguments.PAGE)
                    context.source.useDatabase { runPageAction(page) }
                })
            )
            .then(literal("share")
                .requires(Permissions.require(PlayerStatistics.Permissions.SHARE, true))
                .executes { context -> context.source.shareStoredData(); 0 }
                .then(argument(Arguments.CODE, UuidArgumentType.uuid()).executes { context ->
                    val code = UuidArgumentType.getUuid(context, Arguments.CODE)
                    context.source.shareStoredData(code); 0
                })
            )
        )
    }
}
