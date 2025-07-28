/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav.operation

import android.content.Context
import android.provider.DocumentsContract.buildChildDocumentsUri
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetContentLength
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.GetLastModified
import at.bitfire.dav4jvm.property.webdav.QuotaAvailableBytes
import at.bitfire.dav4jvm.property.webdav.QuotaUsedBytes
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.network.MemoryCookieStore
import at.bitfire.davdroid.webdav.CredentialsStore
import at.bitfire.davdroid.webdav.DavDocumentsProvider
import at.bitfire.davdroid.webdav.DavDocumentsProvider.Companion.DAV_FILE_FIELDS
import at.bitfire.davdroid.webdav.DavDocumentsProviderWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import okhttp3.CookieJar
import okhttp3.logging.HttpLoggingInterceptor
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Acts on behalf of [DavDocumentsProvider].
 *
 * Encapsulates functionality to make it easily testable without generating lots of
 * DocumentProviders during the tests.
 *
 * By containing the actual implementation logic of [DavDocumentsProviderWrapper], it adds a layer of separation
 * to make the methods of [DavDocumentsProviderWrapper] more easily testable.
 * [DavDocumentsProviderWrapper]s methods should do nothing more, but to call [DavDocumentsActor]s methods.
 */
@Deprecated("Move code to respective operations")
class DavDocumentsActor @Inject constructor(
    private val credentialsStore: CredentialsStore,
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val httpClientBuilder: Provider<HttpClient.Builder>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {

    private val cookieStores = mutableMapOf<Long, CookieJar>()

    private val authority = context.getString(R.string.webdav_authority)
    private val documentDao = db.webDavDocumentDao()

    /**
     * Finds children of given parent [WebDavDocument]. After querying, it
     * updates existing children, adds new ones or removes deleted ones.
     *
     * There must never be more than one running instance per [parent]!
     *
     * @param parent    folder to search for children
     */
    internal suspend fun queryChildren(parent: WebDavDocument) {
        val oldChildren = documentDao.getChildren(parent.id).associateBy { it.name }.toMutableMap() // "name" of file/folder must be unique
        val newChildrenList = hashMapOf<String, WebDavDocument>()

        val parentUrl = parent.toHttpUrl(db)
        httpClient(parent.mountId).use { client ->
            val folder = DavCollection(client.okHttpClient, parentUrl)

            try {
                runInterruptible(ioDispatcher) {
                    folder.propfind(1, *DAV_FILE_FIELDS) { response, relation ->
                        logger.fine("$relation $response")

                        val resource: WebDavDocument =
                            when (relation) {
                                Response.HrefRelation.SELF ->       // it's about the parent
                                    parent

                                Response.HrefRelation.MEMBER ->     // it's about a member
                                    WebDavDocument(mountId = parent.mountId, parentId = parent.id, name = response.hrefName())

                                else -> {
                                    // we didn't request this; log a warning and ignore it
                                    logger.warning("Ignoring unexpected $response $relation in $parentUrl")
                                    return@propfind
                                }
                            }

                        val updatedResource = resource.copy(
                            isDirectory = response[ResourceType::class.java]?.types?.contains(ResourceType.COLLECTION)
                                ?: resource.isDirectory,
                            displayName = response[DisplayName::class.java]?.displayName,
                            mimeType = response[GetContentType::class.java]?.type,
                            eTag = response[GetETag::class.java]?.takeIf { !it.weak }?.let { resource.eTag },
                            lastModified = response[GetLastModified::class.java]?.lastModified?.toEpochMilli(),
                            size = response[GetContentLength::class.java]?.contentLength,
                            mayBind = response[CurrentUserPrivilegeSet::class.java]?.mayBind,
                            mayUnbind = response[CurrentUserPrivilegeSet::class.java]?.mayUnbind,
                            mayWriteContent = response[CurrentUserPrivilegeSet::class.java]?.mayWriteContent,
                            quotaAvailable = response[QuotaAvailableBytes::class.java]?.quotaAvailableBytes,
                            quotaUsed = response[QuotaUsedBytes::class.java]?.quotaUsedBytes,
                        )

                        if (resource == parent)
                            documentDao.update(updatedResource)
                        else {
                            documentDao.insertOrUpdate(updatedResource)
                            newChildrenList[resource.name] = updatedResource
                        }

                        // remove resource from known child nodes, because not found on server
                        oldChildren.remove(resource.name)
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't query children", e)
            }
        }

        // Delete child nodes which were not rediscovered (deleted serverside)
        for ((_, oldChild) in oldChildren)
            documentDao.delete(oldChild)
    }


    // helpers

    /**
     * Creates a HTTP client that can be used to access resources in the given mount.
     *
     * @param mountId    ID of the mount to access
     * @param logBody    whether to log the body of HTTP requests (disable for potentially large files)
     */
    internal fun httpClient(mountId: Long, logBody: Boolean = true): HttpClient {
        val builder = httpClientBuilder.get()
            .loggerInterceptorLevel(if (logBody) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.HEADERS)
            .setCookieStore(
                cookieStores.getOrPut(mountId) { MemoryCookieStore() }
            )

        credentialsStore.getCredentials(mountId)?.let { credentials ->
            builder.authenticate(host = null, getCredentials = { credentials })
        }

        return builder.build()
    }

    internal fun notifyFolderChanged(parentDocumentId: Long?) {
        if (parentDocumentId != null)
            context.contentResolver.notifyChange(buildChildDocumentsUri(authority, parentDocumentId.toString()), null)
    }

    internal fun notifyFolderChanged(parentDocumentId: String) {
        context.contentResolver.notifyChange(buildChildDocumentsUri(authority, parentDocumentId), null)
    }

}