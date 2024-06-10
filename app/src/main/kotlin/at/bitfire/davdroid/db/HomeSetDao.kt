/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeSetDao {

    @Query("SELECT * FROM homeset WHERE id=:homesetId")
    fun getById(homesetId: Long): HomeSet

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND url=:url")
    fun getByUrl(serviceId: Long, url: String): HomeSet?

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=(SELECT id FROM service WHERE accountName=:accountName AND type=:serviceType) AND privBind ORDER BY displayName, url COLLATE NOCASE")
    fun getBindableByAccountAndServiceTypeFlow(accountName: String, serviceType: String): Flow<List<HomeSet>>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun getBindableByServiceFlow(serviceId: Long): Flow<List<HomeSet>>

    @Insert
    fun insert(homeSet: HomeSet): Long

    @Update
    fun update(homeset: HomeSet)

    @Delete
    fun delete(homeset: HomeSet)

}