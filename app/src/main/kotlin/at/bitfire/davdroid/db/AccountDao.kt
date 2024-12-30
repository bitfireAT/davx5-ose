/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.jetbrains.annotations.TestOnly

@Dao
interface AccountDao {

    @TestOnly
    @Query("SELECT * FROM account")
    fun getAll(): List<Account>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(account: Account)

    @Query("DELETE FROM account WHERE name=:name")
    fun deleteByName(name: String)

    @Query("DELETE FROM account WHERE name NOT IN (:names)")
    fun deleteExceptNames(names: List<String>)

    @Query("UPDATE account SET name=:newName WHERE name=:oldName")
    fun rename(oldName: String, newName: String)


}