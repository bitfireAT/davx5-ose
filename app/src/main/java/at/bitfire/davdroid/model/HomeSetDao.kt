package at.bitfire.davdroid.model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HomeSetDao: SyncableDao<HomeSet> {

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun getBindableByService(serviceId: Long): List<HomeSet>

    @Query("SELECT COUNT(*) FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun hasBindableByService(serviceId: Long): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long

}