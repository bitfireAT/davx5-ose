/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.Collection

class LocalTaskListStore: LocalDataStore<LocalTaskList> {

    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalTaskList? {
        TODO("Not yet implemented")
    }

    override fun getAll(account: Account, provider: ContentProviderClient): List<LocalTaskList> {
        TODO("Not yet implemented")
    }

    override fun update(provider: ContentProviderClient, localCollection: LocalTaskList, fromCollection: Collection) {
        TODO("Not yet implemented")
    }

    override fun delete(localCollection: LocalTaskList) {
        TODO("Not yet implemented")
    }

}