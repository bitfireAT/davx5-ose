/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.Owner
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient

/**
 * Logic for refreshing the list of collections (and their related information)
 * which do not belong to a home set.
 */
class CollectionsWithoutHomeSetRefresher @AssistedInject constructor(
    @Assisted private val service: Service,
    @Assisted private val httpClient: OkHttpClient,
    private val db: AppDatabase,
    private val collectionRepository: DavCollectionRepository,
) {

    @AssistedFactory
    interface Factory {
        fun create(service: Service, httpClient: OkHttpClient): CollectionsWithoutHomeSetRefresher
    }

    /**
     * Refreshes collections which don't have a homeset.
     *
     * It queries each stored collection with a homeSetId of "null" and either updates or deletes (if inaccessible or unusable) them.
     */
    internal fun refreshCollectionsWithoutHomeSet() {
        val homelessCollections = db.collectionDao().getByServiceAndHomeset(service.id, null).associateBy { it.url }.toMutableMap()
        for((url, localCollection) in homelessCollections) try {
            val collectionProperties = ServiceDetectionUtils.collectionQueryProperties(service.type)
            DavResource(httpClient, url).propfind(0, *collectionProperties) { response, _ ->
                if (!response.isSuccess()) {
                    collectionRepository.delete(localCollection)
                    return@propfind
                }

                // Save or update the collection, if usable, otherwise delete it
                Collection.fromDavResponse(response)?.let { collection ->
                    if (!ServiceDetectionUtils.isUsableCollection(service, collection))
                        return@let
                    collectionRepository.insertOrUpdateByUrlRememberSync(collection.copy(
                        serviceId = localCollection.serviceId,          // use same service ID as previous entry
                        ownerId = response[Owner::class.java]?.href     // save the principal id (collection owner)
                            ?.let { response.href.resolve(it) }
                            ?.let { principalUrl -> Principal.fromServiceAndUrl(service, principalUrl) }
                            ?.let { principal -> db.principalDao().insertOrUpdate(service.id, principal) }
                    ))
                } ?: collectionRepository.delete(localCollection)
            }
        } catch (e: HttpException) {
            // delete collection locally if it was not accessible (40x)
            if (e.code in arrayOf(403, 404, 410))
                collectionRepository.delete(localCollection)
            else
                throw e
        }

    }

}