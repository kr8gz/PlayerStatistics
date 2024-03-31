package io.github.kr8gz.playerstatistics

import io.github.kr8gz.playerstatistics.commands.StatsCommand
import io.github.kr8gz.playerstatistics.database.Database
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object PlayerStatistics : DedicatedServerModInitializer {
    const val MOD_ID = "playerstatistics"
    val MOD_NAME = this::class.simpleName!!

    val LOGGER: Logger = LogManager.getLogger()

    override fun onInitializeServer() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
            StatsCommand(dispatcher, registryAccess).register()
        }

        ServerLifecycleEvents.SERVER_STARTING.register(Database::initialize)

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            Database.updatePlayerProfile(handler.player.gameProfile)
        }
    }
}
