/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import java.util.logging.Logger

/**
 * Used to update the principals (their current display names) and delete those without collections.
 */
class PrincipalsRefresher @AssistedInject constructor(
    @Assisted private val service: Service,
    @Assisted private val httpClient: OkHttpClient,
    private val db: AppDatabase,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(service: Service, httpClient: OkHttpClient): PrincipalsRefresher
    }

    /**
     * Principal properties to ask the server for.
     */
    private val principalProperties = arrayOf(
        DisplayName.NAME,
        ResourceType.NAME
    )

    /**
     * Refreshes the principals (get their current display names).
     * Also removes principals which do not own any collections anymore.
     */
    internal fun refreshPrincipals() {
        // Refresh principals (collection owner urls)
        val principals = db.principalDao().getByService(service.id)
        for (oldPrincipal in principals) {
            val principalUrl = oldPrincipal.url
            logger.fine("Querying principal $principalUrl")
            try {
                DavResource(httpClient, principalUrl).propfind(0, *principalProperties) { response, _ ->
                    if (!response.isSuccess())
                        return@propfind
                    Principal.fromDavResponse(service.id, response)?.let { principal ->
                        logger.fine("Got principal: $principal")
                        db.principalDao().insertOrUpdate(service.id, principal)
                    }
                }
            } catch (e: HttpException) {
                logger.info("Principal update failed with response code ${e.code}. principalUrl=$principalUrl")
            }
        }

        // Delete principals which don't own any collections
        db.principalDao().getAllWithoutCollections().forEach { principal ->
            db.principalDao().delete(principal)
        }
    }

}