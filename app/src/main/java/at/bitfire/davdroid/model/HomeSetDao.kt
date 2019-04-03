package at.bitfire.davdroid.model

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HomeSetDao {

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun observeByService(serviceId: Long): LiveData<List<HomeSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long

    @Insert
    fun insert(homeSets: List<HomeSet>)

    @Query("DELETE FROM homeset WHERE serviceId=:serviceId")
    fun deleteByService(serviceId: Long)

}