package io.github.kr8gz.playerstatistics.extensions

import net.minecraft.stat.ServerStatHandler
import org.apache.commons.io.FilenameUtils
import java.util.UUID

object StatHandler {
    val ServerStatHandler.uuid: UUID
        get() = file.toString()
            .let(FilenameUtils::getBaseName)
            .let(UUID::fromString)
}
