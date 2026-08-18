/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.jetbrains.annotations.TestOnly

// will be used by AccountRepository
@Dao
interface DbAccountDao {
    /**
     * Currently only intended to be used by tests. Proper implementations will be provided at some point.
     */
    @Insert
    @TestOnly
    fun insert(dbAccount: DbAccount): Long

    /**
     * Currently only intended to be used by tests. Proper implementations will be provided at some point.
     */
    @Query("DELETE FROM account")
    fun deleteAllBlocking()
}
