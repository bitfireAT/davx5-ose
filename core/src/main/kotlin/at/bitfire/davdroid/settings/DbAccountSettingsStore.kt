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

class DbAccountSettingsStore @AssistedInject constructor(@Assisted private val account: DbAccount, db: AppDatabase) : AccountSettingsStore {
    private val dao = db.accountSettingDao()

    @AssistedFactory
    interface Factory {
        fun create(account: DbAccount): DbAccountSettingsStore
    }

    override fun getValue(key: String): String? {
        val entry = dao.getBlocking(key)
        return entry?.value
    }

    override fun putValue(key: String, value: String?) {
        val entry = AccountSetting(
            accountId = account.account.id,
            key = key,
            value = value
        )
        dao.insertOrUpdateBlocking(entry)
    }

    override fun getSensitiveValue(key: String): SensitiveString? {
        val entry = dao.getBlocking(key)
        return entry?.sensitiveValue
    }

    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        val entry = AccountSetting(
            accountId = account.account.id,
            key = key,
            sensitiveValue = value
        )
        dao.insertOrUpdateBlocking(entry)
    }
}
