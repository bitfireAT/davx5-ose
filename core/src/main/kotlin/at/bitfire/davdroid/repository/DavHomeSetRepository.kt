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

    fun getByIdBlocking(id: Long) = dao.getById(id)

    fun getByServiceBlocking(serviceId: Long) = dao.getByService(serviceId)

    fun getCalendarHomeSetsFlow(accountId: AccountId) =
        dao.getBindableByAccountAndServiceTypeFlow(accountId, Service.TYPE_CALDAV)

    fun insertOrUpdateByUrlBlocking(homeSet: HomeSet): Long =
        dao.insertOrUpdateByUrlBlocking(homeSet)

    fun deleteBlocking(homeSet: HomeSet) = dao.delete(homeSet)

}