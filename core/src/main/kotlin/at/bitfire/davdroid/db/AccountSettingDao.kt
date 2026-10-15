/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AccountSettingDao {
    /**
     * Inserts a new instance of [setting].
     * [AccountSetting.id] may be generated automatically.
     * @return The generated ID.
     */
    @Insert
    fun insertBlocking(setting: AccountSetting): Long

    @Update
    fun updateBlocking(setting: AccountSetting)

    @Query("SELECT * FROM account_setting WHERE accountId=:accountId AND `key`=:key")
    fun getBlocking(accountId: Long, key: String): AccountSetting?

    @Query("SELECT * FROM account_setting WHERE accountId=:accountId")
    fun getAllBlocking(accountId: Long): List<AccountSetting>

    @Delete
    fun deleteBlocking(setting: AccountSetting)
}
