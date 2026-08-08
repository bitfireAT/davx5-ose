/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.synctools.util.SensitiveString
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class DbAccountSettingsStore @AssistedInject constructor(@Assisted private val account: AccountId, db: AppDatabase) : AccountSettingsStore {
    private val dao = db.dbAccountDao()

    @AssistedFactory
    interface Factory {
        fun create(account: AccountId): DbAccountSettingsStore
    }

    override fun getValue(key: String): String? {
        throw NotImplementedError("The database DAO has not been implemented yet.")
    }

    override fun putValue(key: String, value: String?) {
        throw NotImplementedError("The database DAO has not been implemented yet.")
    }

    override fun getSensitiveValue(key: String): SensitiveString? {
        throw NotImplementedError("The database DAO has not been implemented yet.")
    }

    override fun putSensitiveValue(key: String, value: SensitiveString?) {
        throw NotImplementedError("The database DAO has not been implemented yet.")
    }
}
