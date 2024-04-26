/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Service
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GetBindableHomeSetsFromServiceUseCase @Inject constructor(
    db: AppDatabase
) {

    private val homeSetDao = db.homeSetDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(serviceFlow: Flow<Service?>): Flow<List<HomeSet>> =
        serviceFlow.flatMapLatest { service ->
            if (service == null)
                flowOf(emptyList())
            else
                homeSetDao.getBindableByServiceFlow(service.id)
        }

}