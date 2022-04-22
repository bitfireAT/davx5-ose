/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ServiceDao {

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndType(accountName: String, type: String): Service?

    @Query("SELECT id FROM service WHERE accountName=:accountName AND type=:type")
    fun getIdByAccountAndType(accountName: String, type: String): LiveData<Long>

    @Query("SELECT * FROM service WHERE id=:id")
    fun get(id: Long): Service?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(service: Service): Long

    @Query("DELETE FROM service")
    fun deleteAll()

    @Query("DELETE FROM service WHERE accountName NOT IN (:accountNames)")
    fun deleteExceptAccounts(accountNames: Array<String>)

    @Query("UPDATE service SET accountName=:newName WHERE accountName=:oldName")
    fun renameAccount(oldName: String, newName: String)

}