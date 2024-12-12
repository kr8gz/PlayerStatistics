package io.github.kr8gz.playerstatistics.config

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.watch.ReloadableConfig
import com.sksamuel.hoplite.watch.watchers.FileWatcher
import io.github.kr8gz.playerstatistics.PlayerStatistics
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ConfigLoader {
    private val TEMPLATE_PATH = FabricLoader.getInstance()
        .getModContainer(PlayerStatistics.MOD_ID).get()
        .findPath("config.toml").get()

    private val RUNTIME_PATH = FabricLoader.getInstance()
        .configDir.resolve("${PlayerStatistics.MOD_NAME}.toml")

    private const val ERROR_HINT = "Tip: You can delete the erroneous config file to let it automatically regenerate."

    private fun recreateConfig() {
        PlayerStatistics.LOGGER.info("(Re)creating config file from template")
        Files.copy(TEMPLATE_PATH, RUNTIME_PATH, StandardCopyOption.REPLACE_EXISTING)
    }

    init {
        if (!Files.exists(RUNTIME_PATH)) {
            recreateConfig()
        }
    }

    private val loader = try {
        val loader = ConfigLoader { addPathSource(RUNTIME_PATH); strict() }
        ReloadableConfig(loader, Config::class).apply {
            addWatcher(FileWatcher(RUNTIME_PATH.parent.toString()))
            subscribe { PlayerStatistics.LOGGER.info("Reloaded config") }
            withErrorHandler { exception ->
                if (!Files.exists(RUNTIME_PATH)) {
                    recreateConfig()
                } else {
                    PlayerStatistics.LOGGER.error(exception)
                }
            }
        }
    } catch (e: ConfigException) {
        throw ConfigException("${e.message}\n\n    - $ERROR_HINT\n", e.cause)
    }

    fun getLatest() = loader.getLatest()
}
