/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.repository

import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import javax.inject.Inject

class DavHomeSetRepository @Inject constructor(
    db: AppDatabase
) {

    private val dao = db.homeSetDao()

    fun getAddressBookHomeSetsFlow(accountId: AccountId) =
        dao.getBindableByAccountAndServiceTypeFlow(accountId, Service.TYPE_CARDDAV)

    fun getBindableByServiceFlow(serviceId: Long) = dao.getBindableByServiceFlow(serviceId)

    suspend fun getByService(serviceId: Long) = dao.getByService(serviceId)

    fun getCalendarHomeSetsFlow(accountId: AccountId) =
        dao.getBindableByAccountAndServiceTypeFlow(accountId, Service.TYPE_CALDAV)

    suspend fun insertOrUpdateByUrl(homeSet: HomeSet): Long =
        dao.insertOrUpdateByUrl(homeSet)

    fun insertOrUpdateByUrlBlocking(homeSet: HomeSet): Long =
        dao.insertOrUpdateByUrlBlocking(homeSet)

    suspend fun delete(homeSet: HomeSet) = dao.delete(homeSet)

}