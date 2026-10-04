package com.example.iptvapp.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY groupTitle, name")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY groupTitle, name")
    suspend fun getAll(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    /**
     * Replaces the entire cached playlist in one transaction, so there's no
     * window where the table is empty between the clear and the insert.
     */
    @Transaction
    suspend fun replaceAll(channels: List<ChannelEntity>) {
        clearAll()
        insertAll(channels)
    }
}
