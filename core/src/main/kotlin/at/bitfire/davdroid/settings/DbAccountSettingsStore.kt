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
     * @param key The key to store the value at.
     * @param value The value to store. If `null`, deletes the entry at [key].
     *
     * @throws IllegalArgumentException if the entry at [key] already exists and contains a sensitive value.
     */
    override fun putValue(key: String, value: String?) {
        val existing = dao.getBlocking(accountId, key)
        if (existing != null) {
            check(existing.sensitiveValue == null) { """Key "$key" is already used for a sensitive value""" }

            if (value == null) {
                dao.deleteBlocking(existing)
            } else {
                dao.updateBlocking(existing.copy(value = value))
            }
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
     *
     * @throws IllegalArgumentException if the entry at [key] already exists and contains a non-sensitive value.
     */
    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        val existing = dao.getBlocking(accountId, key)
        if (existing != null) {
            check(existing.value == null) { """Key "$key" is already used for a non-sensitive value""" }

            if (value == null) {
                dao.deleteBlocking(existing)
            } else {
                dao.updateBlocking(existing.copy(sensitiveValue = value))
            }
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
