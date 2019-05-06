package at.bitfire.davdroid.model

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HomeSetDao {

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long

    @Insert
    fun insert(homeSets: List<HomeSet>)

    @Delete
    fun delete(homeSet: HomeSet)

}