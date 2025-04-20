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
interface ServiceDao {

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndType(accountName: String, @ServiceType type: String): Service?

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndTypeFlow(accountName: String, @ServiceType type: String): Flow<Service?>

    @Query("SELECT id FROM service WHERE accountName=:accountName")
    suspend fun getIdsByAccountAsync(accountName: String): List<Long>

    @Query("SELECT * FROM service WHERE id=:id")
    fun get(id: Long): Service?

    @Query("SELECT * FROM service")
    suspend fun getAll(): List<Service>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(service: Service): Long

    @Query("DELETE FROM service")
    fun deleteAll()

    @Query("DELETE FROM service WHERE accountName=:accountName")
    suspend fun deleteByAccount(accountName: String)

    @Query("DELETE FROM service WHERE accountName NOT IN (:accountNames)")
    fun deleteExceptAccounts(accountNames: Array<String>)

    @Query("UPDATE service SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renameAccount(oldName: String, newName: String)

}