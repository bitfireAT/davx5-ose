/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeSetDao {

    @Query("SELECT * FROM homeset WHERE id=:homesetId")
    fun getByIdBlocking(homesetId: Long): HomeSet?

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND url=:url")
    suspend fun getByUrl(serviceId: Long, url: String): HomeSet?

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND url=:url")
    fun getByUrlBlocking(serviceId: Long, url: String): HomeSet?

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    suspend fun getByService(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId")
    fun getByServiceBlocking(serviceId: Long): List<HomeSet>

    @Query("SELECT * FROM homeset WHERE serviceId=(SELECT id FROM service WHERE accountName=:accountName AND type=:serviceType) AND privBind ORDER BY displayName, url COLLATE NOCASE")
    fun getBindableByAccountAndServiceTypeFlow(accountName: String, @ServiceType serviceType: String): Flow<List<HomeSet>>

    fun getBindableByAccountAndServiceTypeFlow(
        accountId: AccountId,
        @ServiceType serviceType: String
    ): Flow<List<HomeSet>> {
        return when (accountId) {
            is LegacyAccount -> {
                getBindableByAccountAndServiceTypeFlow(accountId.androidAccount.name, serviceType)
            }
            else -> TODO("Operation not yet implemented")
        }
    }

    @Query("SELECT * FROM homeset WHERE serviceId=:serviceId AND privBind")
    fun getBindableByServiceFlow(serviceId: Long): Flow<List<HomeSet>>

    @Insert
    suspend fun insert(homeSet: HomeSet): Long

    @Insert
    fun insertBlocking(homeSet: HomeSet): Long

    @Update
    suspend fun update(homeset: HomeSet)

    @Update
    fun updateBlocking(homeset: HomeSet)

    /**
     * If a homeset with the given service ID and URL already exists, it is updated with the other fields.
     * Otherwise, a new homeset is inserted.
     *
     * This method preserves the primary key, as opposed to using "@Insert(onConflict = OnConflictStrategy.REPLACE)"
     * which will create a new row with incremented ID and thus breaks entity relationships!
     *
     * @param homeSet   home set to insert/update
     *
     * @return ID of the row that has been inserted or updated. -1 If the insert fails due to other reasons.
     */
    @Transaction
    suspend fun insertOrUpdateByUrl(homeSet: HomeSet): Long =
        getByUrl(homeSet.serviceId, homeSet.url.toString())?.let { existingHomeset ->
            update(homeSet.copy(id = existingHomeset.id))
            existingHomeset.id
        } ?: insert(homeSet)

    @Transaction
    fun insertOrUpdateByUrlBlocking(homeSet: HomeSet): Long =
        getByUrlBlocking(homeSet.serviceId, homeSet.url.toString())?.let { existingHomeset ->
            updateBlocking(homeSet.copy(id = existingHomeset.id))
            existingHomeset.id
        } ?: insertBlocking(homeSet)

    @Delete
    suspend fun delete(homeset: HomeSet)

    @Delete
    fun deleteBlocking(homeset: HomeSet)

}
