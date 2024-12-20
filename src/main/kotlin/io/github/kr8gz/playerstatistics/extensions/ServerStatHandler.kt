package io.github.kr8gz.playerstatistics.extensions

import io.github.kr8gz.playerstatistics.access.ServerStatHandlerAccess
import net.minecraft.stat.ServerStatHandler
import net.minecraft.stat.Stat
import org.apache.commons.io.FilenameUtils
import java.util.UUID

object ServerStatHandler {
    val ServerStatHandler.uuid: UUID
        get() = file.toString()
            .let(FilenameUtils::getBaseName)
            .let(UUID::fromString)

    fun ServerStatHandler.takeChangedStats(): Map<Stat<*>, Int> {
        return (this as ServerStatHandlerAccess).takeChangedStats()
    }
}
