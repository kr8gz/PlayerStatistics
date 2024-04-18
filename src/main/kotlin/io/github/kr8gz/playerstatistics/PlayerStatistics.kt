package io.github.kr8gz.playerstatistics

import io.github.kr8gz.playerstatistics.command.StatsCommand
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Players
import kotlinx.coroutines.launch
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object PlayerStatistics : DedicatedServerModInitializer {
    const val MOD_ID = "playerstatistics"
    val MOD_NAME = this::class.simpleName!!

    val LOGGER: Logger = LogManager.getLogger()

    object Permissions {
        const val SHARE = "$MOD_ID.share"
    }

    override fun onInitializeServer() {
        StatsCommand

        ServerLifecycleEvents.SERVER_STARTING.register(Database::initialize)

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            Database.launch { Players.updateProfile(handler.player.gameProfile) }
        }
    }
}
