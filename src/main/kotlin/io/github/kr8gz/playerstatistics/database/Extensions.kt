package io.github.kr8gz.playerstatistics.database

import net.minecraft.stat.ServerStatHandler
import org.apache.commons.io.FilenameUtils
import java.util.*

val ServerStatHandler.uuid: UUID
    get() = file.toString()
        .let(FilenameUtils::getBaseName)
        .let(UUID::fromString)
