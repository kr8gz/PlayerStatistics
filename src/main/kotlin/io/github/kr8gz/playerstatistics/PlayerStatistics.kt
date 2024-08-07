package io.github.kr8gz.playerstatistics

import io.github.kr8gz.playerstatistics.commands.StatsCommand
import io.github.kr8gz.playerstatistics.config.config
import io.github.kr8gz.playerstatistics.database.*
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object PlayerStatistics : DedicatedServerModInitializer {
    val MOD_NAME = this::class.simpleName!!
    val MOD_ID = MOD_NAME.lowercase()
    val LOGGER: Logger = LogManager.getLogger()

    override fun onInitializeServer() {
        // register commands
        StatsCommand

        // prepare tables
        Players; Statistics; Leaderboard

        // initialize config
        config

        // SERVER_STARTED rather than SERVER_STARTING to avoid interfering with user cache initialization
        ServerLifecycleEvents.SERVER_STARTED.register { Database.Initializer.start(it) }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            Database.transaction { Players.updateProfile(handler.player.gameProfile) }
        }
    }
}
