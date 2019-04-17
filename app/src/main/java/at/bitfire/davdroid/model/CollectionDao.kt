package at.bitfire.davdroid.model

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collection WHERE id=:id")
    fun get(id: Long): Collection?

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND sync")
    fun getByServiceAndSync(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND type=:type")
    fun observeByServiceAndType(serviceId: Long, type: String): LiveData<List<Collection>>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND supportsVEVENT AND sync")
    fun getSyncCalendars(serviceId: Long): List<Collection>

    @Query("SELECT * FROM collection WHERE serviceId=:serviceId AND supportsVTODO AND sync")
    fun getSyncTaskLists(serviceId: Long): List<Collection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(collection: Collection)

    @Insert
    fun insert(collection: Collection)

    @Insert
    fun insert(collections: List<Collection>)

    @Update
    fun update(collection: Collection)

    @Delete
    fun delete(collection: Collection)

    @Query("DELETE FROM collection WHERE serviceId=:serviceId")
    fun deleteByService(serviceId: Long)


}