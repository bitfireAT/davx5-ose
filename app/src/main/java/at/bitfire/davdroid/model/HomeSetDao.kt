package at.bitfire.davdroid.model

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long


}