package at.bitfire.davdroid.model

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HomeSetDao {

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT COUNT(*) FROM homeset WHERE serviceId=:serviceId")
    fun observeAvailableByService(serviceId: Long): LiveData<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long

    @Insert
    fun insert(homeSets: List<HomeSet>)

    @Query("DELETE FROM homeset WHERE serviceId=:serviceId")
    fun deleteByService(serviceId: Long)

}