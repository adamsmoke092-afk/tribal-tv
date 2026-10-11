package com.example.iptvapp.epg

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EpgDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<EpgProgramme>)

    @Query("DELETE FROM programmes")
    suspend fun clearAll()

    /** Replaces the whole EPG cache atomically — no empty window mid-swap. */
    @Transaction
    suspend fun replaceAll(programmes: List<EpgProgramme>) {
        clearAll()
        insertAll(programmes)
    }

    /**
     * The few programmes for a channel that haven't finished yet, earliest
     * first. selectNowNext() turns these into current/next — fetching a
     * small LIMIT keeps the query cheap and avoids loading a whole day.
     */
    @Query(
        "SELECT * FROM programmes WHERE channelId = :channelId AND stopMs > :nowMs " +
            "ORDER BY startMs ASC LIMIT 4"
    )
    suspend fun upcomingForChannel(channelId: String, nowMs: Long): List<EpgProgramme>

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int
}
