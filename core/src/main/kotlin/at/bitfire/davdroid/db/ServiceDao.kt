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
    @Deprecated("Use getByAccountAndType(accountId: AccountId, type: String) instead")
    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    suspend fun getByAccountAndType(accountName: String, @ServiceType type: String): Service?

    @Query("SELECT * FROM service WHERE accountId=:accountId AND type=:type")
    suspend fun getByAccountAndType(accountId: Long, @ServiceType type: String): Service?

    @Deprecated("Use getByAccountAndTypeFlow(accountId: AccountId, type: String) instead")
    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndTypeBlocking(accountName: String, @ServiceType type: String): Service?

    @Query("SELECT * FROM service WHERE accountId=:accountId AND type=:type")
    fun getByAccountAndTypeBlocking(accountId: Long, @ServiceType type: String): Service?

    suspend fun getByAccountIdAndType(accountId: AccountId, @ServiceType type: String): Service? {
        return when (accountId) {
            is LegacyAccount -> getByAccountAndType(accountId.androidAccount.name, type)
            is DbAccountId -> getByAccountAndType(accountId.id, type)
        }
    }

    fun getByAccountIdAndTypeBlocking(accountId: AccountId, @ServiceType type: String): Service? {
        return when (accountId) {
            is LegacyAccount -> getByAccountAndTypeBlocking(accountId.androidAccount.name, type)
            is DbAccountId -> getByAccountAndTypeBlocking(accountId.id, type)
        }
    }

    @Deprecated("Use getByAccountAndTypeFlow(accountId: AccountId, type: String) instead")
    @Query("SELECT * FROM service WHERE accountName=:accountName AND type=:type")
    fun getByAccountAndTypeFlow(accountName: String, @ServiceType type: String): Flow<Service?>

    @Query("SELECT * FROM service WHERE accountId=:accountId AND type=:type")
    fun getByAccountAndTypeFlow(accountId: Long, @ServiceType type: String): Flow<Service?>

    fun getByAccountAndTypeFlow(accountId: AccountId, @ServiceType type: String): Flow<Service?> {
        return when (accountId) {
            is LegacyAccount -> getByAccountAndTypeFlow(accountId.androidAccount.name, type)
            is DbAccountId -> getByAccountAndTypeFlow(accountId.id, type)
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

    @Deprecated("Use deleteByAccount(accountId: AccountId) instead")
    @Query("DELETE FROM service WHERE accountName=:accountName")
    suspend fun deleteByAccount(accountName: String)

    @Query("DELETE FROM service WHERE accountId=:accountId")
    suspend fun deleteByAccount(accountId: Long)

    suspend fun deleteByAccount(accountId: AccountId) {
        when (accountId) {
            is LegacyAccount -> deleteByAccount(accountId.androidAccount.name)
            is DbAccountId -> deleteByAccount(accountId.id)
        }
    }

    @Query("DELETE FROM service WHERE accountName NOT IN (:accountNames)")
    fun deleteExceptAccountsBlocking(accountNames: List<String>)

    @Query("UPDATE service SET accountName=:newName WHERE accountName=:oldName")
    suspend fun renameAccount(oldName: String, newName: String)

}