package at.bitfire.davdroid.model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ServiceDao {

    @Query("SELECT * FROM service WHERE accountName=:accountName")
    fun getByAccount(accountName: String): List<Service>

    @Query("SELECT * FROM service WHERE accountName=:accountName")
    fun observeByAccount(accountName: String): LiveData<List<Service>>

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndType(accountName: String, type: String): Service?

    @Query("SELECT id FROM service WHERE accountName=:accountName AND type=:type")
    fun getIdByAccountAndType(accountName: String, type: String): Long?

    @Query("SELECT * FROM service WHERE id=:id")
    fun getById(id: Long): Service?

    @Query("SELECT * FROM service WHERE type=:type")
    fun getByType(type: String): List<Service>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(service: Service): Long

    @Query("DELETE FROM service")
    fun deleteAll()

    @Query("DELETE FROM service WHERE accountName NOT IN (:accountNames)")
    fun deleteExceptAccounts(accountNames: Array<String>)

    @Query("UPDATE service SET accountName=:newName WHERE accountName=:oldName")
    fun renameAccount(oldName: String, newName: String)

}