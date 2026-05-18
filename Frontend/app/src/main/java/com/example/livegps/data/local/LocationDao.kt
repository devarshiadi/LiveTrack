package com.example.livegps.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room access for the offline location buffer. */
@Dao
interface LocationDao {

    @Insert
    suspend fun insert(location: LocationEntity)

    /** Oldest unsent fixes first — preserves track order when draining. */
    @Query("SELECT * FROM buffered_locations ORDER BY timestamp ASC, id ASC LIMIT :limit")
    suspend fun oldestBatch(limit: Int): List<LocationEntity>

    @Query("DELETE FROM buffered_locations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM buffered_locations")
    fun countFlow(): Flow<Int>

    /** Caps the buffer: keeps only the newest [keep] rows, drops the rest. */
    @Query(
        "DELETE FROM buffered_locations WHERE id NOT IN " +
            "(SELECT id FROM buffered_locations ORDER BY id DESC LIMIT :keep)",
    )
    suspend fun pruneToNewest(keep: Int)
}
