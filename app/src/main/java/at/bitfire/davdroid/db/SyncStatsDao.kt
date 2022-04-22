/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface SyncStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(syncStats: SyncStats)

}