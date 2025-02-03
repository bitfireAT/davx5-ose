/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.CollectionType
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Gets a list of collections for a service and type, optionally filtered by "show only personal" setting.
 *
 * Takes the "force read-only address books" setting into account: if set, all address books will have "forceReadOnly" set.
 */
class GetServiceCollectionPagerUseCase @Inject constructor(
    val collectionRepository: DavCollectionRepository,
    val settings: SettingsManager
) {

    companion object {
        const val PAGER_SIZE = 20
    }

    val forceReadOnlyAddressBooksFlow = settings.getBooleanFlow(Settings.FORCE_READ_ONLY_ADDRESSBOOKS, false)


    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        serviceFlow: Flow<Service?>,
        @CollectionType collectionType: String,
        showOnlyPersonalFlow: Flow<Boolean>
    ): Flow<PagingData<Collection>> =
        combine(serviceFlow, showOnlyPersonalFlow, forceReadOnlyAddressBooksFlow) { service, onlyPersonal, forceReadOnlyAddressBooks ->
            service?.let { service ->
                val dataFlow = Pager(
                    config = PagingConfig(PAGER_SIZE),
                    pagingSourceFactory = {
                        if (onlyPersonal == true)
                            collectionRepository.pagePersonalByServiceAndType(service.id, collectionType)
                        else
                            collectionRepository.pageByServiceAndType(service.id, collectionType)
                    }
                ).flow

                // set "forceReadOnly" for every address book if requested
                if (forceReadOnlyAddressBooks && collectionType == Collection.TYPE_ADDRESSBOOK)
                    dataFlow.map { pagingData ->
                        pagingData.map { collection ->
                            collection.copy(forceReadOnly = true)
                        }
                    }
                else
                    dataFlow
            } ?: flowOf(PagingData.empty())
        }.flatMapLatest { it }

}