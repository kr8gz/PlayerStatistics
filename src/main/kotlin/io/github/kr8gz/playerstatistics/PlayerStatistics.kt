package io.github.kr8gz.playerstatistics

import io.github.kr8gz.playerstatistics.commands.StatsCommand
import io.github.kr8gz.playerstatistics.database.*
import net.fabricmc.api.DedicatedServerModInitializer
import net.silkmc.silk.core.annotations.ExperimentalSilkApi
import net.silkmc.silk.core.event.PlayerEvents
import net.silkmc.silk.core.event.ServerEvents
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object PlayerStatistics : DedicatedServerModInitializer {
    val MOD_NAME = this::class.simpleName!!
    val LOGGER: Logger = LogManager.getLogger()

    @OptIn(ExperimentalSilkApi::class)
    override fun onInitializeServer() {
        // initialize command
        StatsCommand

        // initialize tables
        Players
        Statistics
        Leaderboard

        // run after server start to avoid interfering with user cache initialization
        ServerEvents.postStart.listen { event ->
            Database.Initializer.run(event.server)
        }

        PlayerEvents.preLogin.listen { event ->
            Database.transaction { Players.updateProfile(event.player.gameProfile) }
        }
    }
}
