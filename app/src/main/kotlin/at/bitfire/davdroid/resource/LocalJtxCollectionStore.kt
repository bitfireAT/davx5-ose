/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.Collection

class LocalJtxCollectionStore: LocalDataStore<LocalJtxCollection> {

    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalJtxCollection? {
        TODO("Not yet implemented")
    }

    override fun getAll(account: Account, provider: ContentProviderClient): List<LocalJtxCollection> {
        TODO("Not yet implemented")
    }

    override fun update(provider: ContentProviderClient, localCollection: LocalJtxCollection, fromCollection: Collection) {
        TODO("Not yet implemented")
    }

    override fun delete(localCollection: LocalJtxCollection) {
        TODO("Not yet implemented")
    }

}