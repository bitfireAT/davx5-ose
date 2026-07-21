/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.servicedetection

import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.ktor.responses
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Principal
import at.bitfire.davdroid.db.Service
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import java.util.logging.Logger
import javax.annotation.WillNotClose

/**
 * Used to update the principals (their current display names) and delete those without collections.
 */
class PrincipalsRefresher @AssistedInject constructor(
    @Assisted private val service: Service,
    @Assisted @WillNotClose private val httpClient: HttpClient,
    private val db: AppDatabase,
    private val logger: Logger
) {

    @AssistedFactory
    interface Factory {
        fun create(service: Service, httpClient: HttpClient): PrincipalsRefresher
    }

    /**
     * Principal properties to ask the server for.
     */
    private val principalProperties = arrayOf(
        WebDAV.DisplayName,
        WebDAV.ResourceType
    )

    /**
     * Refreshes the principals (get their current display names).
     * Also removes principals which do not own any collections anymore.
     */
    suspend fun refreshPrincipals() {
        // Refresh principals (collection owner urls)
        val principals = db.principalDao().getByService(service.id)
        for (oldPrincipal in principals) {
            val principalUrl = oldPrincipal.url
            logger.fine("Querying principal $principalUrl")
            try {
                DavResource(httpClient, principalUrl).propfind(0, *principalProperties)
                    .responses()
                    .collect { response ->
                        if (!response.isSuccess())
                            return@collect
                        Principal.fromDavResponse(service.id, response)?.let { principal ->
                            logger.fine("Got principal: $principal")
                            db.principalDao().insertOrUpdate(service.id, principal)
                        }
                    }
            } catch (e: HttpException) {
                logger.info("Principal update failed with response code ${e.statusCode}. principalUrl=$principalUrl")
            }
        }

        // Delete principals which don't own any collections
        db.principalDao().getAllWithoutCollections().forEach { principal ->
            db.principalDao().delete(principal)
        }
    }

}
