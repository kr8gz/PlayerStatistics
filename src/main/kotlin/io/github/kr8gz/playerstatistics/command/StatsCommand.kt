package io.github.kr8gz.playerstatistics.command

import com.mojang.brigadier.arguments.IntegerArgumentType
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
import net.minecraft.registry.Registries
import net.minecraft.resource.featuretoggle.ToggleableFeature
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.Stat
import net.minecraft.stat.StatType
import net.minecraft.stat.Stats
import net.minecraft.text.Text
import net.silkmc.silk.commands.CommandBuilder
import net.silkmc.silk.commands.command
import java.util.*
import java.util.concurrent.ConcurrentHashMap

typealias ServerCommandContext = CommandContext<ServerCommandSource>

typealias CommandNodeBuilder = CommandBuilder<ServerCommandSource, *, *>
typealias ArgumentBuilder<T> = CommandNodeBuilder.(ServerCommandContext.() -> T) -> Unit

object StatsCommand {
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

    private inline fun ServerCommandContext.usingDatabase(crossinline command: suspend ServerCommandContext.() -> Unit) {
        if (Database.Initializer.inProgress) throw Exceptions.DATABASE_INITIALIZING.create()
        if (!databaseUsers.add(source.uuid)) throw Exceptions.ALREADY_RUNNING.create()

        Database.launch {
            source.server.playerManager.playerList.forEach {
                Database.Updater.updateStats(it.statHandler)
            }
            command()
            databaseUsers.remove(source.uuid)
        }
    }

    private fun CommandNodeBuilder.statArgument(builder: ArgumentBuilder<Stat<*>>) {
        fun <T> CommandNodeBuilder.addArgumentsForStatType(statType: StatType<T>, shortIds: Boolean = false) {
            val argumentType = { registryAccess: CommandRegistryAccess ->
                RegistryEntryArgumentType.registryEntry(registryAccess, statType.registry.key)
            }
            argument(Arguments.STAT, argumentType) { stat ->
                if (shortIds) brigadier {
                    suggests { _, builder ->
                        CommandSource.suggestMatching(statType.registry.ids.map { it.toShortString() }, builder)
                    }
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

    private inline fun CommandNodeBuilder.playerArgument(optional: Boolean = false, builder: ArgumentBuilder<String>) {
        argument<String>(Arguments.PLAYER) { player ->
            brigadier {
                suggests { _, builder ->
                    CommandSource.suggestMatching(Database.playerNames, builder)
                }
            }
            builder(player)
        }
        if (optional) builder { source.playerOrThrow.gameProfile.name }
    }

    private inline fun CommandNodeBuilder.executes(crossinline command: ServerCommandContext.() -> Unit) {
        brigadier {
            executes { it.command(); 0 }
        }
    }

    init {
        command("stats") {
            literal("leaderboard") {
                statArgument { stat ->
                    executes {
                        usingDatabase { source.sendLeaderboard(stat()) }
                    }
                }
            }

            literal("total") {
                statArgument { stat ->
                    executes {
                        usingDatabase { source.sendServerTotal(stat()) }
                    }
                }
            }

            literal("player") {
                playerArgument { player ->
                    statArgument { stat ->
                        executes {
                            usingDatabase { source.sendPlayerStat(stat(), player()) }
                        }
                    }
                }
            }

            literal("top") {
                playerArgument(optional = true) {
                    executes {
                        val player = it()
                        usingDatabase { source.sendPlayerTopStats(player) }
                    }
                }
            }

            literal("page") {
                argument(Arguments.PAGE, IntegerArgumentType.integer(1)) { page ->
                    executes {
                        usingDatabase { source.runPageAction(page()) }
                    }
                }
            }

            literal("share") {
                requires { Permissions.check(it, PlayerStatistics.Permissions.SHARE, true) }
                executes { source.shareStoredData() }
                argument<UUID>(Arguments.CODE) { code ->
                    executes { source.shareStoredData(code()) }
                }
            }
        }
    }
}
