package com.example.iptvapp.epg

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * EPG cache access.
 *
 * Every mutation is scoped to ONE source (a guide URL): a refresh replaces
 * only its own rows, so a guide that fails, changes shape or returns nothing
 * can never erase another guide's data. There is deliberately no whole-table
 * replace — that is what used to let one bad guide wipe every other.
 */
@Dao
interface EpgDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<EpgProgramme>)

    @Query("DELETE FROM programmes WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /** Drops programmes that have already ended (keeps the cache bounded). */
    @Query("DELETE FROM programmes WHERE stopMs < :nowMs")
    suspend fun deleteExpired(nowMs: Long)

    /**
     * Replaces exactly one source's rows in a single transaction: no window
     * where that source's data is missing, and other sources untouched.
     */
    @Transaction
    suspend fun replaceSource(source: String, programmes: List<EpgProgramme>) {
        deleteBySource(source)
        insertAll(programmes)
    }

    /**
     * Programmes for a channel that have not ended yet, earliest first, across
     * the sources configured for the CURRENT playlist (never another
     * playlist's rows, even for an identical channel id).
     *
     * Ordered by (startMs, source) so that when two sources describe the same
     * channel and start time the caller sees a deterministic sequence —
     * selectNowNext() relies on that for stable Now/Next output.
     *
     * Callers must pass a NON-EMPTY [sources] list: an empty IN() clause is
     * invalid SQL, and "no sources configured" is decided before querying.
     */
    @Query(
        "SELECT * FROM programmes WHERE channelId = :channelId AND stopMs > :nowMs " +
            "AND source IN (:sources) " +
            "ORDER BY startMs ASC, source ASC LIMIT 8"
    )
    suspend fun upcomingForChannel(channelId: String, sources: List<String>, nowMs: Long): List<EpgProgramme>

    @Query("SELECT COUNT(*) FROM programmes WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM programmes WHERE source IN (:sources)")
    suspend fun countBySources(sources: List<String>): Int

    @Query("SELECT COUNT(DISTINCT channelId) FROM programmes WHERE source IN (:sources)")
    suspend fun countDistinctChannelsFor(sources: List<String>): Int

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int

    @Query("SELECT DISTINCT source FROM programmes")
    suspend fun distinctSources(): List<String>
}
