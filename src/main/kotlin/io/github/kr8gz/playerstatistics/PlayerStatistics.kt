package io.github.kr8gz.playerstatistics

import io.github.kr8gz.playerstatistics.commands.StatsCommand
import io.github.kr8gz.playerstatistics.database.Database
import io.github.kr8gz.playerstatistics.database.Players
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object PlayerStatistics : DedicatedServerModInitializer {
    const val MOD_ID = "playerstatistics"
    val MOD_NAME = this::class.simpleName!!

    val LOGGER: Logger = LogManager.getLogger()

    override fun onInitializeServer() {
        StatsCommand

        ServerLifecycleEvents.SERVER_STARTING.register(Database.Initializer::invoke)

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            Database.transaction { Players.updateProfile(handler.player.gameProfile) }
        }
    }
}
