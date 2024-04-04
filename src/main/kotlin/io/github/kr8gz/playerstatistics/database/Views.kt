package io.github.kr8gz.playerstatistics.database

import java.sql.Statement

sealed class View(val viewName: String) {
    override fun toString() = viewName

    protected abstract val definition: String

    companion object {
        fun createAll(statement: Statement) {
            View::class.sealedSubclasses.mapNotNull { it.objectInstance }.forEach {
                statement.executeUpdate("CREATE VIEW IF NOT EXISTS ${it.viewName} AS ${it.definition}")
            }
        }
    }
}

object RankedStatistics : View("ranked_statistics") {
    const val rank = "rank"

    override val definition = with(Statistics) { """
        SELECT *, RANK() OVER (PARTITION BY $statistic ORDER BY $value DESC) $rank
        FROM $Statistics
    """ }
}
