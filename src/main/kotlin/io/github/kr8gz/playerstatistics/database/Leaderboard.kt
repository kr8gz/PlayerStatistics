package io.github.kr8gz.playerstatistics.database

import kotlinx.coroutines.coroutineScope
import net.minecraft.stat.Stat
import java.sql.ResultSet

class Leaderboard<T>(val pageEntries: List<Entry<T>>, val pageCount: Int) {
    data class Entry<T>(val pos: Int, val rank: Int, val key: T, val value: Int)

    companion object : Database.View("ranked_statistics") {
        private const val pos = "pos"
        private const val rank = "rank"
        private const val pageCount = "page_count"

        override val definition = with(Statistics) { """
            SELECT *, RANK() OVER (PARTITION BY $stat ORDER BY $value DESC) $rank
            FROM $Statistics
        """ }

        /** Default chat size = 10, minus rows for header and footer */
        const val pageSize = 8

        private inline fun <T> ResultSet.generateLeaderboard(crossinline entryBuilder: ResultSet.() -> Entry<T>?): Leaderboard<T>? {
            return mutableListOf<Entry<T>>().takeIf { next() }?.let { entries ->
                val pages = getInt(pageCount)
                do entryBuilder()?.let(entries::add) while (next())
                Leaderboard(entries, pages)
            }
        }

        /** @return key = player name */
        suspend fun forStat(stat: Stat<*>, highlightedName: String? = null, page: Int): Leaderboard<String>? = coroutineScope {
            Database.prepareStatement("""
                WITH leaderboard AS (
                    SELECT *, ROW_NUMBER() OVER (ORDER BY $rank, ${Statistics.player}) $pos
                    FROM $Leaderboard
                    JOIN $Players ON ${Players.uuid} = ${Statistics.player}
                    WHERE ${Statistics.stat} = ?
                ),
                highlight AS (SELECT $pos highlight FROM leaderboard WHERE ${Players.name} = ? LIMIT 1),
                page_offset AS (SELECT $pageSize - EXISTS(SELECT 1 FROM highlight) page_offset),
                $pageCount AS (
                    SELECT MAX(highlight IS NOT NULL, CEIL(1.0 * (MAX($pos) - (highlight IS NOT NULL)) / page_offset)) $pageCount
                    FROM leaderboard LEFT JOIN highlight, page_offset
                )
                SELECT $pos, $rank, ${Players.name}, ${Statistics.value}, $pageCount
                FROM leaderboard LEFT JOIN highlight, page_offset, $pageCount
                WHERE $pos >  page_offset * ${page - 1} + COALESCE(highlight <= page_offset * ${page - 1}, 0)
                  AND $pos <= page_offset * $page       + COALESCE(highlight <= page_offset * $page,       0)
                   OR $pos = highlight
                ORDER BY $pos
            """).run {
                setString(1, stat.name)
                setString(2, highlightedName)
                executeQuery()
            }.generateLeaderboard {
                Entry(getInt(pos), getInt(rank), getString(Players.name), getInt(Statistics.value))
            }
        }

        /** @return key = stat */
        suspend fun forPlayer(name: String, page: Int): Leaderboard<Stat<*>>? = coroutineScope {
            Database.prepareStatement("""
                WITH leaderboard AS (
                    SELECT *, ROW_NUMBER() OVER (ORDER BY $rank, ${Statistics.value} DESC) $pos
                    FROM $Leaderboard
                    JOIN $Players ON ${Players.uuid} = ${Statistics.player}
                    WHERE ${Players.name} = ? AND ${Statistics.value} > 0
                )
                SELECT
                    $pos, $rank, ${Statistics.stat}, ${Statistics.value},
                    CEIL(1.0 * (SELECT MAX($pos) FROM leaderboard) / $pageSize) $pageCount
                FROM leaderboard
                WHERE $pos > ${pageSize * (page - 1)}
                ORDER BY $pos
                LIMIT $pageSize
            """).run {
                setString(1, name)
                executeQuery()
            }.generateLeaderboard {
                Statistics.parseStat(getString(Statistics.stat))?.let { stat ->
                    Entry(getInt(pos), getInt(rank), stat, getInt(Statistics.value))
                }
            }
        }
    }
}
