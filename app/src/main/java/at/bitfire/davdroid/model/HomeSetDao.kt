package at.bitfire.davdroid.model

import androidx.room.*

@Dao
interface HomeSetDao: SyncableDao<HomeSet> {

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByService(serviceId: Long): List<HomeSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(homeSet: HomeSet): Long


}