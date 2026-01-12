/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.CollectionType
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
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

    val forceReadOnlyAddressBooksFlow = settings.getBooleanFlow(Settings.FORCE_READ_ONLY_ADDRESSBOOKS, false)


    /**
     * Combines multiple flows into a flow of paged collections for the given service and collection type,
     * with optional filtering for personal collections only. Applies the force read-only setting for address
     * book collections if enabled. The returned flow will emit new up-to-date collection paging data when
     * - any of the input flows changes,
     * - any of the requested collections changes in DB or
     * - request matching collections are added/removed in DB.
     *
     * @param serviceFlow Flow emitting the Service which collections should be fetched (null for no service)
     * @param collectionType Type of collections to fetch (address books, calendars, etc.)
     * @param showOnlyPersonalFlow Flow to determine whether to show only personal collections
     * @return Flow of PagingData containing the requested collections
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        serviceFlow: Flow<Service?>,
        @CollectionType collectionType: String,
        showOnlyPersonalFlow: Flow<Boolean>,
        viewModelScope: CoroutineScope
    ): Flow<PagingData<Collection>> =
        combine(serviceFlow, showOnlyPersonalFlow, forceReadOnlyAddressBooksFlow) { service, onlyPersonal, forceReadOnlyAddressBooks ->
            if (service == null)
                return@combine flowOf(PagingData.empty())

            val dataFlow = Pager(
                config = PagingConfig(PAGER_SIZE),
                pagingSourceFactory = {
                    collectionRepository.pageByServiceAndType(service.id, collectionType, onlyPersonal)
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
        }.flatMapLatest { it }
            .cachedIn(viewModelScope)

    companion object {
        const val PAGER_SIZE = 20
    }

}