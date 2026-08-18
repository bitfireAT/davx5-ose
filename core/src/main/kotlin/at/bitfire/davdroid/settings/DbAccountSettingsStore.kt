/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.db.AccountSetting
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.synctools.util.SensitiveString
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class DbAccountSettingsStore @AssistedInject constructor(@Assisted account: DbAccountId, db: AppDatabase) : AccountSettingsStore {
    private val accountId = account.id

    private val dao = db.accountSettingDao()

    @AssistedFactory
    interface Factory {
        fun create(account: DbAccountId): DbAccountSettingsStore
    }

    /**
     * Fetches an entry from the database with [key] and for the specified account.
     *
     * Note: Values written with [putSensitiveValue] are not visible here — they can only be retrieved with
     * [getSensitiveValue], and are overridden if [putValue] is called with the same [key].
     *
     * @return The value stored, or `null` if not set.
     */
    override fun getValue(key: String): String? {
        val entry = dao.getBlocking(accountId, key)
        return entry?.value
    }

    /**
     * Stores the given [value] at [key] for the specified account.
     *
     * Note: Overrides any value previously stored at [key] with [putSensitiveValue].
     *
     * @param key The key to store the value at.
     * @param value The value to store. If `null`, deletes the entry at [key].
     */
    override fun putValue(key: String, value: String?) {
        if (value == null) {
            dao.getBlocking(accountId, key)?.let { setting ->
                dao.deleteBlocking(setting)
            }
            return
        }

        val existing = dao.getBlocking(accountId, key)
        if (existing != null) {
            // overwrite value and clear any sensitive value that may have been stored at this key
            dao.updateBlocking(existing.copy(value = value, sensitiveValue = null))
        } else {
            val entry = AccountSetting(
                accountId = accountId,
                key = key,
                value = value
            )
            dao.insertBlocking(entry)
        }
    }

    /**
     * Fetches a sensitive entry from the database with [key] and for the specified account.
     *
     * Note: Values written with [putValue] are not visible here — they can only be retrieved with [getValue],
     * and are overridden if [putSensitiveValue] is called with the same [key].
     *
     * @return The value stored, or `null` if not set.
     */
    override fun getSensitiveValue(key: String): SensitiveString? {
        val entry = dao.getBlocking(accountId, key)
        return entry?.sensitiveValue
    }

    /**
     * Stores the given [value] at [key] for the specified account.
     *
     * Note: Overrides any value previously stored at [key] with [putValue].
     *
     * @param key The key to store the value at.
     * @param value The value to store. If `null`, deletes the entry at [key].
     */
    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        if (value == null) {
            dao.getBlocking(accountId, key)?.let { setting ->
                dao.deleteBlocking(setting)
            }
            return
        }

        val existing = dao.getBlocking(accountId, key)
        if (existing != null) {
            // overwrite sensitive value and clear any regular value that may have been stored at this key
            dao.updateBlocking(existing.copy(sensitiveValue = value, value = null))
        } else {
            val entry = AccountSetting(
                accountId = accountId,
                key = key,
                sensitiveValue = value
            )
            dao.insertBlocking(entry)
        }
    }
}
