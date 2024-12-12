package io.github.kr8gz.playerstatistics.database

import com.mojang.authlib.GameProfile
import kotlinx.coroutines.coroutineScope
import java.util.UUID

object Players : Database.Table("players") {
    const val uuid = "uuid"
    const val name = "name"

    override val schema = listOf(
        "$uuid BINARY(16) NOT NULL PRIMARY KEY",
        "$name VARCHAR(16) NOT NULL COLLATE NOCASE",
    )

    private val nameMap by Database.Initializer {
        executeQuery("SELECT * FROM $Players").use { rs ->
            generateSequence {
                rs.takeIf { it.next() }?.run {
                    UUID.fromString(getString(uuid)) to getString(name)
                }
            }.toMap(HashMap())
        }
    }

    val nameList get() = nameMap.values.sorted()

    suspend fun updateProfile(profile: GameProfile): Unit = coroutineScope {
        Database.prepareStatement { """
            INSERT INTO $Players ($uuid, $name) VALUES (${param(profile.id.toString())}, ${param(profile.name)})
            ON CONFLICT($uuid) DO UPDATE SET $name = excluded.$name
        """ }.executeUpdate()
        nameMap[profile.id] = profile.name
    }

    suspend fun fixName(search: String): String? = coroutineScope {
        Database.prepareStatement { "SELECT $name FROM $Players WHERE $name = ${param(search)}" }
            .executeQuery().use { rs -> rs.takeIf { it.next() }?.getString(name) }
    }
}
