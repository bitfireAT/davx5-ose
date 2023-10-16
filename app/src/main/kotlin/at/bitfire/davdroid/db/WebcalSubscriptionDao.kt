package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WebcalSubscriptionDao {

    @Query("SELECT * FROM webcal_subscription WHERE collectionId=:collectionId")
    fun getByCollectionId(collectionId: Long): WebcalSubscription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(subscription: WebcalSubscription): Long

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