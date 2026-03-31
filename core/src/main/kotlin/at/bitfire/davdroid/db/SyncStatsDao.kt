/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(syncStats: SyncStats): Long

    /**
     * Updates the count of items for a given sync stats entry. If the entry does not exist, it does nothing.
     */
    suspend fun updateCount(id: Long, count: Long) {
        val existing = getById(id)
        if (existing != null) {
            insertOrReplace(existing.copy(count = count))
        }
    }

    @Query("SELECT * FROM syncstats WHERE id=:id")
    suspend fun getById(id: Long): SyncStats?

    @Query("SELECT * FROM syncstats WHERE collectionId=:id")
    fun getByCollectionIdFlow(id: Long): Flow<List<SyncStats>>

}