/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.jetbrains.annotations.TestOnly

// will be used by AccountRepository
@Dao
interface DbAccountDao {
    @Query("SELECT * FROM account WHERE id = :id")
    fun getBlocking(id: Long): DbAccount?

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun get(id: Long): DbAccount?

    @Query("SELECT * FROM account WHERE name = :name")
    suspend fun getFromName(name: String): DbAccount?

    @Insert
    fun insertBlocking(dbAccount: DbAccount): Long

    @Insert
    suspend fun insert(dbAccount: DbAccount): Long

    @Update
    fun updateBlocking(dbAccount: DbAccount)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE account SET name = :newName WHERE id = :id")
    suspend fun rename(id: Long, newName: String)

    @Delete
    fun deleteBlocking(dbAccount: DbAccount)

    @TestOnly
    @Query("DELETE FROM account")
    fun deleteAllBlocking()
}
