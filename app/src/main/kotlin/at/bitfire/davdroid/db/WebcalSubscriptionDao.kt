package at.bitfire.davdroid.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface WebcalSubscriptionDao {

    @Query("SELECT * FROM webcal_subscription ORDER BY displayName, url")
    suspend fun getAllAsync(): List<WebcalSubscription>

    @Query("SELECT * FROM webcal_subscription ORDER BY displayName, url")
    fun getAllLive(): LiveData<List<WebcalSubscription>>

    @Query("SELECT COUNT(*) FROM webcal_subscription")
    fun getCount(): Int

    @Query("SELECT * FROM webcal_subscription WHERE collectionId=:collectionId")
    fun getByCollectionId(collectionId: Long): WebcalSubscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(subscription: WebcalSubscription): Long

    @Update
    fun update(subscription: WebcalSubscription)

    @Delete
    fun delete(subscription: WebcalSubscription)


    @Transaction
    fun insertAndUpdateCollection(collectionDao: CollectionDao, subscription: WebcalSubscription) {
        insert(subscription)
        subscription.collectionId?.let { collectionId ->
            collectionDao.updateSync(collectionId, true)
        }
    }

}