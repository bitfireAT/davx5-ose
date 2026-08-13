/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDao {

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    suspend fun getByAccountAndType(accountName: String, @ServiceType type: String): Service?

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndTypeBlocking(accountName: String, @ServiceType type: String): Service?

    suspend fun getByAccountIdAndType(accountId: AccountId, @ServiceType type: String): Service? {
        return when (accountId) {
            is LegacyAccount -> getByAccountAndType(accountId.androidAccount.name, type)
            is DbAccountId -> TODO("Currently not possible to get services by DbAccount")
        }
    }

    fun getByAccountIdAndTypeBlocking(accountId: AccountId, @ServiceType type: String): Service? {
        return when (accountId) {
            is LegacyAccount -> getByAccountAndTypeBlocking(accountId.androidAccount.name, type)
            is DbAccountId -> TODO("Currently not possible to get services by DbAccount")
        }
    }

    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndTypeFlow(accountName: String, @ServiceType type: String): Flow<Service?>

    fun getByAccountAndTypeFlow(accountId: AccountId, @ServiceType type: String): Flow<Service?> {
        return when (accountId) {
            is LegacyAccount -> getByAccountAndTypeFlow(accountId.androidAccount.name, type)
            is DbAccountId -> TODO("Currently not possible to get services by DbAccount")
        }
    }

    @Query("SELECT id FROM service WHERE accountName=:accountName")
    suspend fun getIdsByAccount(accountName: String): List<Long>

    @Query("SELECT * FROM service WHERE id=:id")
    suspend fun get(id: Long): Service?

    @Query("SELECT * FROM service WHERE id=:id")
    fun getBlocking(id: Long): Service?

    @Query("SELECT * FROM service")
    suspend fun getAll(): List<Service>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplaceBlocking(service: Service): Long

    @Query("DELETE FROM service")
    fun deleteAllBlocking()

    @Query("DELETE FROM service WHERE accountName=:accountName")
    suspend fun deleteByAccount(accountName: String)

    suspend fun deleteByAccount(accountId: AccountId) {
        when (accountId) {
            is LegacyAccount -> deleteByAccount(accountId.androidAccount.name)
            is DbAccountId -> TODO("Currently not possible to delete services by DbAccount")
        }
    }

    @Query("DELETE FROM service WHERE accountName NOT IN (:accountNames)")
    fun deleteExceptAccountsBlocking(accountNames: List<String>)

    @Query("UPDATE service SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renameAccount(oldName: String, newName: String)

}