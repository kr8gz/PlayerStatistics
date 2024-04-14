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
import net.minecraft.command.argument.RegistryEntryArgumentType
import net.minecraft.registry.Registries
import net.minecraft.resource.featuretoggle.ToggleableFeature
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.stat.*
import net.minecraft.text.Text
import net.silkmc.silk.commands.command
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import net.silkmc.silk.commands.CommandBuilder as SilkCommandBuilder

typealias ServerCommandContext = CommandContext<ServerCommandSource>

typealias CommandBuilder = SilkCommandBuilder<ServerCommandSource, *, *>
typealias ArgumentBuilder<T> = CommandBuilder.(ServerCommandContext.() -> T) -> Unit

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

    private inline infix fun ServerCommandContext.usingDatabase(crossinline command: suspend ServerCommandContext.() -> Unit) {
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

    private fun CommandBuilder.statArgument(builder: ArgumentBuilder<Stat<*>>) {
        fun <T> CommandBuilder.addArgumentsForStatType(statType: StatType<T>, shortIds: Boolean = false) {
            val argumentType = { registryAccess: CommandRegistryAccess ->
                RegistryEntryArgumentType.registryEntry(registryAccess, statType.registry.key)
            }
            argument(Arguments.STAT, argumentType) { stat ->
                if (shortIds) suggestList {
                    statType.registry.ids.map { it.toShortString() }
                }
                builder { statType.getOrCreateStat(stat().value()) }
            }
        }

        Registries.STAT_TYPE.entrySet.forEach { (key, statType) ->
            // add custom ones directly to the outer level to make them easier to find
            if (statType == Stats.CUSTOM) {
                addArgumentsForStatType(statType, shortIds = true)
            } else {
                literal(key.value.path) {
                    addArgumentsForStatType(statType)
                }
            }
        }

        literal("random") {
            builder {
                fun <T> StatType<T>.getAll() = registry
                    .filter { it !is ToggleableFeature || it.isEnabled(source.enabledFeatures) }
                    .map(::getOrCreateStat)

                Registries.STAT_TYPE.flatMap { it.getAll() }.random()
            }
        }
    }

    private inline fun CommandBuilder.playerArgument(builder: ArgumentBuilder<String>) {
        argument<String>(Arguments.PLAYER) { player ->
            suggestList { Database.playerNames }
            builder(player)
        }
    }

    init {
        command("stats") {
            literal("leaderboard") {
                statArgument { stat ->
                    runs {
                        usingDatabase { source.sendLeaderboard(stat()) }
                    }
                }
            }

            literal("total") {
                statArgument { stat ->
                    runs {
                        usingDatabase { source.sendServerTotal(stat()) }
                    }
                }
            }

            literal("player") {
                playerArgument { player ->
                    statArgument { stat ->
                        runs {
                            usingDatabase { source.sendPlayerStat(stat(), player()) }
                        }
                    }
                }
            }

            literal("top") {
                runs {
                    val player = source.playerOrThrow.gameProfile.name
                    usingDatabase { source.sendPlayerTopStats(player) }
                }
                playerArgument { player ->
                    runs {
                        usingDatabase { source.sendPlayerTopStats(player()) }
                    }
                }
            }

            literal("page") {
                argument(Arguments.PAGE, IntegerArgumentType.integer(1)) { page ->
                    runs {
                        usingDatabase { source.runPageAction(page()) }
                    }
                }
            }

            literal("share") {
                requires { Permissions.check(it, PlayerStatistics.Permissions.SHARE, true) }
                runs { source.shareStoredData() }
                argument<UUID>(Arguments.CODE) { code ->
                    runs { source.shareStoredData(code()) }
                }
            }
        }
    }
}
