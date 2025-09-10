/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.Owner
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavHomeSetRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Used to update the list of synchronizable collections
 */
class HomeSetRefresher @AssistedInject constructor(
    @Assisted private val service: Service,
    @Assisted private val httpClient: OkHttpClient,
    private val db: AppDatabase,
    private val logger: Logger,
    private val collectionRepository: DavCollectionRepository,
    private val homeSetRepository: DavHomeSetRepository,
    private val settings: SettingsManager
) {

    @AssistedFactory
    interface Factory {
        fun create(service: Service, httpClient: OkHttpClient): HomeSetRefresher
    }

    /**
     * Refreshes home-sets and their collections.
     *
     * Each stored home-set URL is queried (`PROPFIND`) and its collections are either saved, updated
     * or marked as "without home-set" - in case a collection was removed from its home-set.
     *
     * If a home-set URL in fact points to a collection directly, the collection will be saved with this URL,
     * and a null value for it's home-set. Refreshing of collections without home-sets is then handled by [CollectionsWithoutHomeSetRefresher.refreshCollectionsWithoutHomeSet].
     */
    internal fun refreshHomesetsAndTheirCollections() {
        val homesets = homeSetRepository.getByServiceBlocking(service.id).associateBy { it.url }.toMutableMap()
        for ((homeSetUrl, localHomeset) in homesets) {
            logger.fine("Listing home set $homeSetUrl")

            // To find removed collections in this homeset: create a queue from existing collections and remove every collection that
            // is successfully rediscovered. If there are collections left, after processing is done, these are marked as "without home-set".
            val localHomesetCollections = db.collectionDao()
                .getByServiceAndHomeset(service.id, localHomeset.id)
                .associateBy { it.url }
                .toMutableMap()

            try {
                val collectionProperties = ServiceDetectionUtils.collectionQueryProperties(service.type)
                DavResource(httpClient, homeSetUrl).propfind(1, *collectionProperties) { response, relation ->
                    // Note: This callback may be called multiple times ([MultiResponseCallback])
                    if (!response.isSuccess())
                        return@propfind

                    if (relation == Response.HrefRelation.SELF)
                    // this response is about the home set itself
                        homeSetRepository.insertOrUpdateByUrlBlocking(
                            localHomeset.copy(
                                displayName = response[DisplayName::class.java]?.displayName,
                                privBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind != false
                            )
                        )

                    // in any case, check whether the response is about a usable collection
                    var collection = Collection.fromDavResponse(response) ?: return@propfind
                    collection = collection.copy(
                        serviceId = service.id,
                        homeSetId = localHomeset.id,
                        sync = shouldPreselect(collection, homesets.values),
                        ownerId = response[Owner::class.java]?.href  // save the principal id (collection owner)
                            ?.let { response.href.resolve(it) }
                            ?.let { principalUrl -> Principal.fromServiceAndUrl(service, principalUrl) }
                            ?.let { principal -> db.principalDao().insertOrUpdate(service.id, principal) }
                    )
                    logger.log(Level.FINE, "Found collection", collection)

                    // save or update collection if usable (ignore it otherwise)
                    if (ServiceDetectionUtils.isUsableCollection(service, collection))
                        collectionRepository.insertOrUpdateByUrlRememberSync(collection)

                    // Remove this collection from queue - because it was found in the home set
                    localHomesetCollections.remove(collection.url)
                }
            } catch (e: HttpException) {
                // delete home set locally if it was not accessible (40x)
                if (e.statusCode in arrayOf(403, 404, 410))
                    homeSetRepository.deleteBlocking(localHomeset)
            }

            // Mark leftover (not rediscovered) collections from queue as "without home-set" (remove association)
            for ((_, collection) in localHomesetCollections)
                collectionRepository.insertOrUpdateByUrlRememberSync(
                    collection.copy(homeSetId = null)
                )

        }
    }

    /**
     * Whether to preselect the given collection for synchronisation, according to the
     * settings [Settings.PRESELECT_COLLECTIONS] (see there for allowed values) and
     * [Settings.PRESELECT_COLLECTIONS_EXCLUDED].
     *
     * A collection is considered _personal_ if it is found in one of the current-user-principal's home-sets.
     *
     * Before a collection is pre-selected, we check whether its URL matches the regexp in
     * [Settings.PRESELECT_COLLECTIONS_EXCLUDED], in which case *false* is returned.
     *
     * @param collection    the collection to check
     * @param homeSets      list of personal home-sets
     * @return *true* if the collection should be preselected for synchronization; *false* otherwise
     */
    internal fun shouldPreselect(collection: Collection, homeSets: Iterable<HomeSet>): Boolean {
        val shouldPreselect = settings.getIntOrNull(Settings.PRESELECT_COLLECTIONS)

        val excluded by lazy {
            val excludedRegex = settings.getString(Settings.PRESELECT_COLLECTIONS_EXCLUDED)
            if (!excludedRegex.isNullOrEmpty())
                Regex(excludedRegex).containsMatchIn(collection.url.toString())
            else
                false
        }

        return when (shouldPreselect) {
            Settings.PRESELECT_COLLECTIONS_ALL ->
                // preselect if collection url is not excluded
                !excluded

            Settings.PRESELECT_COLLECTIONS_PERSONAL ->
                // preselect if is personal (in a personal home-set), but not excluded
                homeSets
                    .filter { homeset -> homeset.personal }
                    .map { homeset -> homeset.id }
                    .contains(collection.homeSetId)
                        && !excluded

            else -> // don't preselect
                false
        }
    }

}