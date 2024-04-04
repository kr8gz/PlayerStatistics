package io.github.kr8gz.playerstatistics.database

import java.sql.Statement

sealed class Table(val tableName: String) {
    override fun toString() = tableName

    protected abstract val schema: List<String>

    companion object {
        fun createAll(statement: Statement) {
            Table::class.sealedSubclasses.mapNotNull { it.objectInstance }.forEach {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS ${it.tableName} (${it.schema.joinToString()})")
            }
        }
    }
}

object Players : Table("players") {
    const val uuid = "uuid"
    const val name = "name"

    override val schema = listOf(
            "$uuid BINARY(16) NOT NULL PRIMARY KEY",
            "$name VARCHAR(16) NOT NULL",
    )
}

object Statistics : Table("statistics") {
    const val playerUUID = "player"
    const val statistic = "stat"
    const val value = "value"

    override val schema = listOf(
            "$playerUUID BINARY(16) NOT NULL",
            "$statistic TEXT NOT NULL",
            "$value INT NOT NULL",
            "PRIMARY KEY ($playerUUID, $statistic)",
    )
}
