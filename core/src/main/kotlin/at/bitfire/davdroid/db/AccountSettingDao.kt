/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AccountSettingDao {
    @Insert
    fun insertBlocking(setting: AccountSetting)

    @Insert
    fun updateBlocking(setting: AccountSetting)

    @Query("SELECT * FROM account_setting WHERE accountId=:accountId AND `key`=:key")
    fun getBlocking(accountId: Long, key: String): AccountSetting?

    @Delete
    fun deleteBlocking(setting: AccountSetting)

    /**
     * If an entry already exists with [setting]'s [AccountSetting.key], updates it with [updateBlocking], otherwise,
     * inserts it as a new entry using [insertBlocking].
     */
    fun insertOrUpdateBlocking(setting: AccountSetting) {
        val existing = getBlocking(setting.accountId, setting.key)
        if (existing == null)
            insertBlocking(setting)
        else
            // We update the id of the given setting to the existing one, to make sure it's correctly set
            updateBlocking(setting.copy(id = existing.id))
    }
}
