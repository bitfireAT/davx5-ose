/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import android.accounts.Account
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import javax.inject.Inject

class DavHomeSetRepository @Inject constructor(
    db: AppDatabase
) {

    val dao = db.homeSetDao()

    fun getAddressBookHomeSetsFlow(account: Account) =
        dao.getBindableByAccountAndServiceTypeFlow(account.name, Service.TYPE_CARDDAV)

    fun getCalendarHomeSetsFlow(account: Account) =
        dao.getBindableByAccountAndServiceTypeFlow(account.name, Service.TYPE_CALDAV)

}