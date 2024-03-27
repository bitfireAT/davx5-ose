/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface HomeSetDao {

    @Query("SELECT * FROM homeset WHERE id=:homesetId")
    fun getById(homesetId: Long): HomeSet

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND url=:url")
    fun getByUrl(serviceId: Long, url: String): HomeSet?

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun getBindableByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun getLiveBindableByService(serviceId: Long): LiveData<List<HomeSet>>

    @Insert
    fun insert(homeSet: HomeSet): Long

    @Update
    fun update(homeset: HomeSet)

    /**
     * Tries to insert new row, but updates existing row if already present.
     * This method preserves the primary key, as opposed to using "@Insert(onConflict = OnConflictStrategy.REPLACE)"
     * which will create a new row with incremented ID and thus breaks entity relationships!
     *
     * @return ID of the row, that has been inserted or updated. -1 If the insert fails due to other reasons.
     */
    @Transaction
    fun insertOrUpdateByUrl(homeset: HomeSet): Long =
        getByUrl(homeset.serviceId, homeset.url.toString())?.let { existingHomeset ->
            update(homeset.copy(id = existingHomeset.id))
            existingHomeset.id
        } ?: insert(homeset)

    @Delete
    fun delete(homeset: HomeSet)

}