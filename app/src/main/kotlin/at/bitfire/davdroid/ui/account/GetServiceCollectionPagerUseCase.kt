/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.paging.Pager
import androidx.paging.PagingConfig
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.AccountSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetServiceCollectionPagerUseCase @Inject constructor(
    val db: AppDatabase
) {

    companion object {
        const val PAGER_SIZE = 20
    }

    operator fun invoke(
        serviceFlow: Flow<Service?>,
        collectionType: String,
        showOnlyPersonalFlow: Flow<AccountSettings.ShowOnlyPersonal?>
    ): Flow<Pager<Int, Collection>?> =
        combine(serviceFlow, showOnlyPersonalFlow) { service, onlyPersonal ->
            if (service == null)
                return@combine null

            Pager(
                config = PagingConfig(AccountModel.PAGER_SIZE),
                pagingSourceFactory = {
                    if (onlyPersonal?.onlyPersonal == true)
                        db.collectionDao().pagePersonalByServiceAndType(service.id, collectionType)
                    else
                        db.collectionDao().pageByServiceAndType(service.id, collectionType)
                }
            )
        }

}