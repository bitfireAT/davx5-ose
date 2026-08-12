/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.DbAccount
import at.bitfire.davdroid.db.AccountSetting
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.synctools.util.SensitiveString
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class DbAccountSettingsStore @AssistedInject constructor(@Assisted account: DbAccount, db: AppDatabase) : AccountSettingsStore {
    private val accountId = account.account.id

    private val dao = db.accountSettingDao()

    @AssistedFactory
    interface Factory {
        fun create(account: DbAccount): DbAccountSettingsStore
    }

    /**
     * Fetches an entry from the database with [key] and for the specified account.
     *
     * Note: May return `null` if not stored the value with [putValue].
     *
     * @return The value stored, or `null` otherwise.
     */
    override fun getValue(key: String): String? {
        val entry = dao.getBlocking(accountId, key)
        return entry?.value
    }

    /**
     * Stores the given [value] at [key] for the specified value.
     *
     * Note: The data may only be retrieved with [getValue].
     * Note 2: Only deletes entries stored by [putValue]. Otherwise, doesn't do anything.
     *
     * @param key The key to store the value at.
     * @param value The value to store. If `null`, deletes the entry at [key].
     */
    override fun putValue(key: String, value: String?) {
        if (value == null) {
            dao.getBlocking(accountId, key)
                // only allow to delete values set by putValue
                ?.takeIf { it.value != null }
                ?.let {
                    dao.deleteBlocking(it)
                }
            return
        }

        val entry = AccountSetting(
            accountId = accountId,
            key = key,
            value = value
        )
        dao.insertOrUpdateBlocking(entry)
    }

    /**
     * Fetches a sensitive entry from the database with [key] and for the specified account.
     *
     * Note: May return `null` if not stored the value with [putSensitiveValue].
     *
     * @return The value stored, or `null` otherwise.
     */
    override fun getSensitiveValue(key: String): SensitiveString? {
        val entry = dao.getBlocking(accountId, key)
        return entry?.sensitiveValue
    }

    /**
     * Stores the given [value] at [key] for the specified **safe** value.
     *
     * Note: The data may only be retrieved with [getSensitiveValue].
     * Note 2: Only deletes entries stored by [putSensitiveValue]. Otherwise, doesn't do anything.
     *
     * @param key The key to store the value at.
     * @param value The value to store. If `null`, deletes the entry at [key].
     */
    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        if (value == null) {
            dao.getBlocking(accountId, key)
                // only allow to delete values set by putSensitiveValue
                ?.takeIf { it.sensitiveValue != null }
                ?.let {
                    dao.deleteBlocking(it)
                }
            return
        }

        val entry = AccountSetting(
            accountId = accountId,
            key = key,
            sensitiveValue = value
        )
        dao.insertOrUpdateBlocking(entry)
    }
}
