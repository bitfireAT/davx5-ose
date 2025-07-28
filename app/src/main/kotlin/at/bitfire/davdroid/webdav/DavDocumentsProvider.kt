/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.ThumbnailUtils
import android.net.ConnectivityManager
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.buildChildDocumentsUri
import android.provider.DocumentsContract.buildRootsUri
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import androidx.core.app.TaskStackBuilder
import androidx.core.content.getSystemService
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.HttpException
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
import at.bitfire.davdroid.db.WebDavDocumentDao
import at.bitfire.davdroid.di.IoDispatcher
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.network.MemoryCookieStore
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import at.bitfire.davdroid.webdav.cache.ThumbnailCache
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Provider

/**
 * Provides functionality on WebDav documents.
 *
 * Actual implementation should go into [DavDocumentsActor].
 */
class DavDocumentsProvider(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DavDocumentsProviderEntryPoint {
        fun appDatabase(): AppDatabase
        fun credentialsStore(): CredentialsStore
        fun davDocumentsActorFactory(): DavDocumentsActor.Factory
        fun documentSortByMapper(): DocumentSortByMapper
        fun logger(): Logger
        fun randomAccessCallbackWrapperFactory(): RandomAccessCallbackWrapper.Factory
        fun streamingFileDescriptorFactory(): StreamingFileDescriptor.Factory
        fun thumbnailCache(): ThumbnailCache

        fun impl(): DavDocumentsProviderImpl
    }

    companion object {
        val DAV_FILE_FIELDS = arrayOf(
            ResourceType.NAME,
            CurrentUserPrivilegeSet.NAME,
            DisplayName.NAME,
            GetETag.NAME,
            GetContentType.NAME,
            GetContentLength.NAME,
            GetLastModified.NAME,
            QuotaAvailableBytes.NAME,
            QuotaUsedBytes.NAME,
        )

        const val MAX_NAME_ATTEMPTS = 5
        const val THUMBNAIL_TIMEOUT_MS = 15000L

        fun notifyMountsChanged(context: Context) {
            context.contentResolver.notifyChange(buildRootsUri(context.getString(R.string.webdav_authority)), null)
        }

    }

    val documentProviderScope = CoroutineScope(SupervisorJob())

    private val ourContext by lazy { context!! }        // requireContext() requires API level 30
    private val authority by lazy { ourContext.getString(R.string.webdav_authority) }
    private lateinit var entryPoint: DavDocumentsProviderEntryPoint

    private val logger by lazy { entryPoint.logger() }

    private val db by lazy { entryPoint.appDatabase() }
    private val mountDao by lazy { db.webDavMountDao() }
    private val documentDao by lazy { db.webDavDocumentDao() }

    private val thumbnailCache by lazy { entryPoint.thumbnailCache() }

    private val connectivityManager by lazy { ourContext.getSystemService<ConnectivityManager>()!! }
    private val storageManager by lazy { ourContext.getSystemService<StorageManager>()!! }

    /** List of currently active [queryChildDocuments] runners.
     *
     *  Key: document ID (directory) for which children are listed.
     *  Value: whether the runner is still running (*true*) or has already finished (*false*).
     */
    private val runningQueryChildren = ConcurrentHashMap<Long, Boolean>()

    private val credentialsStore by lazy { entryPoint.credentialsStore() }
    private val cookieStore by lazy { mutableMapOf<Long, CookieJar>() }
    private val actor by lazy { entryPoint.davDocumentsActorFactory().create(cookieStore, credentialsStore) }

    override fun onCreate(): Boolean {
        entryPoint = EntryPointAccessors.fromApplication<DavDocumentsProviderEntryPoint>(context!!)
        return true
    }

    override fun shutdown() {
        documentProviderScope.cancel()
    }


    /*** query ***/

    override fun queryRoots(projection: Array<out String>?) =
        entryPoint.impl().queryRoots(projection)

    override fun queryDocument(documentId: String, projection: Array<out String>?) =
        entryPoint.impl().queryDocument(documentId, projection)

    /**
     * Gets old or new children of given parent.
     *
     * Dispatches a worker querying the server for new children of given parent, and instantly
     * returns old children (or nothing, on initial call).
     * Once the worker finishes its query, it notifies the [android.content.ContentResolver] about
     * change, which calls this method again. The worker being done
     */
    @Synchronized
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        logger.fine("WebDAV queryChildDocuments $parentDocumentId $projection $sortOrder")
        val parentId = parentDocumentId.toLong()
        val parent = documentDao.get(parentId) ?: throw FileNotFoundException()

        val columns = projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )

        // Register watcher
        val result = DocumentsCursor(columns)
        val notificationUri = buildChildDocumentsUri(authority, parentDocumentId)
        result.setNotificationUri(ourContext.contentResolver, notificationUri)

        // Dispatch worker querying for the children and keep track of it
        val running = runningQueryChildren.getOrPut(parentId) {
            documentProviderScope.launch {
                actor.queryChildren(parent)
                // Once the query is done, set query as finished (not running)
                runningQueryChildren[parentId] = false
                // .. and notify - effectively calling this method again
                ourContext.contentResolver.notifyChange(notificationUri, null)
            }
            true
        }

        if (running)        // worker still running
            result.loading = true
        else                // remove worker from list if done
            runningQueryChildren.remove(parentId)

        // Prepare SORT BY clause
        val mapper = entryPoint.documentSortByMapper()
        val sqlSortBy = if (sortOrder != null)
            mapper.mapContentProviderToSql(sortOrder)
        else
            WebDavDocumentDao.DEFAULT_ORDER

        // Regardless of whether the worker is done, return the children we already have
        val children = documentDao.getChildren(parentId, sqlSortBy)
        for (child in children) {
            val bundle = child.toBundle(parent)
            result.addRow(bundle)
        }

        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        logger.fine("WebDAV isChildDocument $parentDocumentId $documentId")
        val parent = documentDao.get(parentDocumentId.toLong()) ?: throw FileNotFoundException()

        var iter: WebDavDocument? = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        while (iter != null) {
            val currentParentId = iter.parentId
            if (currentParentId == parent.id)
                return true

            iter = if (currentParentId != null)
                documentDao.get(currentParentId)
            else
                null
        }
        return false
    }


    /*** copy/create/delete/move/rename ***/

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String = runBlocking {
        logger.fine("WebDAV copyDocument $sourceDocumentId $targetParentDocumentId")
        val srcDoc = documentDao.get(sourceDocumentId.toLong()) ?: throw FileNotFoundException()
        val dstFolder = documentDao.get(targetParentDocumentId.toLong()) ?: throw FileNotFoundException()
        val name = srcDoc.name

        if (srcDoc.mountId != dstFolder.mountId)
            throw UnsupportedOperationException("Can't COPY between WebDAV servers")

        actor.httpClient(srcDoc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, srcDoc.toHttpUrl(db))
            val dstUrl = dstFolder.toHttpUrl(db).newBuilder()
                .addPathSegment(name)
                .build()

            try {
                runInterruptible(ioDispatcher) {
                    dav.copy(dstUrl, false) {
                        // successfully copied
                    }
                }
            } catch (e: HttpException) {
                e.throwForDocumentProvider()
            }

            val dstDocId = documentDao.insertOrReplace(
                WebDavDocument(
                    mountId = dstFolder.mountId,
                    parentId = dstFolder.id,
                    name = name,
                    isDirectory = srcDoc.isDirectory,
                    displayName = srcDoc.displayName,
                    mimeType = srcDoc.mimeType,
                    size = srcDoc.size
                )
            ).toString()

            actor.notifyFolderChanged(targetParentDocumentId)

            /* return */ dstDocId
        }
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? = runBlocking {
        logger.fine("WebDAV createDocument $parentDocumentId $mimeType $displayName")
        val parent = documentDao.get(parentDocumentId.toLong()) ?: throw FileNotFoundException()
        val createDirectory = mimeType == Document.MIME_TYPE_DIR

        var docId: Long? = null
        actor.httpClient(parent.mountId).use { client ->
            for (attempt in 0..MAX_NAME_ATTEMPTS) {
                val newName = displayNameToMemberName(displayName, attempt)
                val parentUrl = parent.toHttpUrl(db)
                val newLocation = parentUrl.newBuilder()
                    .addPathSegment(newName)
                    .build()
                val doc = DavResource(client.okHttpClient, newLocation)
                try {
                    runInterruptible(ioDispatcher) {
                        if (createDirectory)
                            doc.mkCol(null) {
                                // directory successfully created
                            }
                        else
                            doc.put("".toRequestBody(null), ifNoneMatch = true) {
                                // document successfully created
                            }
                    }

                    docId = documentDao.insertOrReplace(
                        WebDavDocument(
                            mountId = parent.mountId,
                            parentId = parent.id,
                            name = newName,
                            mimeType = mimeType.toMediaTypeOrNull(),
                            isDirectory = createDirectory
                        )
                    )

                    actor.notifyFolderChanged(parentDocumentId)

                    return@runBlocking docId.toString()
                } catch (e: HttpException) {
                    e.throwForDocumentProvider(ignorePreconditionFailed = true)
                }
            }
        }

        null
    }

    override fun deleteDocument(documentId: String) = runBlocking {
        logger.fine("WebDAV removeDocument $documentId")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        actor.httpClient(doc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, doc.toHttpUrl(db))
            try {
                runInterruptible(ioDispatcher) {
                    dav.delete {
                        // successfully deleted
                    }
                }
                logger.fine("Successfully removed")
                documentDao.delete(doc)

                actor.notifyFolderChanged(doc.parentId)
            } catch (e: HttpException) {
                e.throwForDocumentProvider()
            }
        }
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String = runBlocking {
        logger.fine("WebDAV moveDocument $sourceDocumentId $sourceParentDocumentId $targetParentDocumentId")
        val doc = documentDao.get(sourceDocumentId.toLong()) ?: throw FileNotFoundException()
        val dstParent = documentDao.get(targetParentDocumentId.toLong()) ?: throw FileNotFoundException()

        if (doc.mountId != dstParent.mountId)
            throw UnsupportedOperationException("Can't MOVE between WebDAV servers")

        val newLocation = dstParent.toHttpUrl(db).newBuilder()
            .addPathSegment(doc.name)
            .build()

        actor.httpClient(doc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, doc.toHttpUrl(db))
            try {
                runInterruptible(ioDispatcher) {
                    dav.move(newLocation, false) {
                        // successfully moved
                    }
                }

                documentDao.update(doc.copy(parentId = dstParent.id))

                actor.notifyFolderChanged(sourceParentDocumentId)
                actor.notifyFolderChanged(targetParentDocumentId)
            } catch (e: HttpException) {
                e.throwForDocumentProvider()
            }
        }

        doc.id.toString()
    }

    override fun renameDocument(documentId: String, displayName: String): String? = runBlocking {
        logger.fine("WebDAV renameDocument $documentId $displayName")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        actor.httpClient(doc.mountId).use { client ->
            for (attempt in 0..MAX_NAME_ATTEMPTS) {
                val newName = displayNameToMemberName(displayName, attempt)
                val oldUrl = doc.toHttpUrl(db)
                val newLocation = oldUrl.newBuilder()
                    .removePathSegment(oldUrl.pathSegments.lastIndex)
                    .addPathSegment(newName)
                    .build()
                try {
                    val dav = DavResource(client.okHttpClient, oldUrl)
                    runInterruptible(ioDispatcher) {
                        dav.move(newLocation, false) {
                            // successfully renamed
                        }
                    }
                    documentDao.update(doc.copy(name = newName))

                    actor.notifyFolderChanged(doc.parentId)

                    return@runBlocking doc.id.toString()
                } catch (e: HttpException) {
                    e.throwForDocumentProvider(true)
                }
            }
        }

        null
    }

    private fun displayNameToMemberName(displayName: String, appendNumber: Int = 0): String {
        val safeName = displayName.filterNot { it.isISOControl() }

        if (appendNumber != 0) {
            val extension: String? = MimeTypeMap.getFileExtensionFromUrl(displayName)
            if (extension != null) {
                val baseName = safeName.removeSuffix(".$extension")
                return "${baseName}_$appendNumber.$extension"
            } else
                return "${safeName}_$appendNumber"
        } else
            return safeName
    }


    /*** read/write ***/

    private suspend fun headRequest(client: HttpClient, url: HttpUrl): HeadResponse = runInterruptible(ioDispatcher) {
        HeadResponse.fromUrl(client, url)
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = runBlocking {
        logger.fine("WebDAV openDocument $documentId $mode $signal")

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val url = doc.toHttpUrl(db)
        val client = actor.httpClient(doc.mountId, logBody = false)

        val modeFlags = ParcelFileDescriptor.parseMode(mode)
        val readAccess = when (mode) {
            "r" -> true
            "w", "wt" -> false
            else -> throw UnsupportedOperationException("Mode $mode not supported by WebDAV")
        }

        val accessScope = CoroutineScope(SupervisorJob())
        signal?.setOnCancelListener {
            logger.fine("Cancelling WebDAV access to $url")
            accessScope.cancel()
        }

        val fileInfo = accessScope.async {
            headRequest(client, url)
        }.await()
        logger.fine("Received file info: $fileInfo")

        // RandomAccessCallback.Wrapper / StreamingFileDescriptor are responsible for closing httpClient
        return@runBlocking if (
            Build.VERSION.SDK_INT >= 26 &&      // openProxyFileDescriptor exists since Android 8.0
            readAccess &&                       // WebDAV doesn't support random write access natively
            fileInfo.size != null &&            // file descriptor must return a useful value on getFileSize()
            (fileInfo.eTag != null || fileInfo.lastModified != null) &&     // we need a method to determine whether the document has changed during access
            fileInfo.supportsPartial == true    // WebDAV server must support random access
        ) {
            logger.fine("Creating RandomAccessCallback for $url")
            val factory = entryPoint.randomAccessCallbackWrapperFactory()
            val accessor = factory.create(client, url, doc.mimeType, fileInfo, accessScope)
            storageManager.openProxyFileDescriptor(modeFlags, accessor, accessor.workerHandler)
        } else {
            logger.fine("Creating StreamingFileDescriptor for $url")
            val factory = entryPoint.streamingFileDescriptorFactory()
            val fd = factory.create(client, url, doc.mimeType, accessScope) { transferred ->
                // called when transfer is finished

                val now = System.currentTimeMillis()
                if (!readAccess /* write access */) {
                    // write access, update file size
                    documentDao.update(doc.copy(size = transferred, lastModified = now))
                }

                actor.notifyFolderChanged(doc.parentId)
            }

            if (readAccess)
                fd.download()
            else
                fd.upload()
        }
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point, signal: CancellationSignal?): AssetFileDescriptor? {
        logger.info("openDocumentThumbnail documentId=$documentId sizeHint=$sizeHint signal=$signal")

        if (connectivityManager.isActiveNetworkMetered)
            // don't download the large images just to create a thumbnail on metered networks
            return null

        if (signal == null) {
            logger.warning("openDocumentThumbnail without cancellationSignal causes too much problems, please fix calling app")
            return null
        }
        val accessScope = CoroutineScope(SupervisorJob())
        signal.setOnCancelListener {
            logger.fine("Cancelling thumbnail generation for $documentId")
            accessScope.cancel()
        }

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()

        val docCacheKey = doc.cacheKey()
        if (docCacheKey == null) {
            logger.warning("openDocumentThumbnail won't generate thumbnails when document state (ETag/Last-Modified) is unknown")
            return null
        }

        val thumbFile = thumbnailCache.get(docCacheKey, sizeHint) {
            // create thumbnail
            val job = accessScope.async {
                withTimeout(THUMBNAIL_TIMEOUT_MS) {
                    actor.httpClient(doc.mountId, logBody = false).use { client ->
                        val url = doc.toHttpUrl(db)
                        val dav = DavResource(client.okHttpClient, url)
                        var result: ByteArray? = null
                        runInterruptible(ioDispatcher) {
                            dav.get("image/*", null) { response ->
                                response.body.byteStream().use { data ->
                                    BitmapFactory.decodeStream(data)?.let { bitmap ->
                                        val thumb = ThumbnailUtils.extractThumbnail(bitmap, sizeHint.x, sizeHint.y)
                                        val baos = ByteArrayOutputStream()
                                        thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                                        result = baos.toByteArray()
                                    }
                                }
                            }
                        }
                        result
                    }
                }
            }

            try {
                runBlocking {
                    job.await()
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't generate thumbnail", e)
                null
            }
        }

        if (thumbFile != null)
            return AssetFileDescriptor(
                ParcelFileDescriptor.open(thumbFile, ParcelFileDescriptor.MODE_READ_ONLY),
                0, thumbFile.length()
            )

        return null
    }


    /**
     * Acts on behalf of [DavDocumentsProvider].
     *
     * Encapsulates functionality to make it easily testable without generating lots of
     * DocumentProviders during the tests.
     *
     * By containing the actual implementation logic of [DavDocumentsProvider], it adds a layer of separation
     * to make the methods of [DavDocumentsProvider] more easily testable.
     * [DavDocumentsProvider]s methods should do nothing more, but to call [DavDocumentsActor]s methods.
     */
    class DavDocumentsActor @AssistedInject constructor(
        @Assisted private val cookieStores: MutableMap<Long, CookieJar>,
        @Assisted private val credentialsStore: CredentialsStore,
        @ApplicationContext private val context: Context,
        private val db: AppDatabase,
        private val httpClientBuilder: Provider<HttpClient.Builder>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val logger: Logger
    ) {

        @AssistedFactory
        interface Factory {
            fun create(cookieStore: MutableMap<Long, CookieJar>, credentialsStore: CredentialsStore): DavDocumentsActor
        }

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


    private fun HttpException.throwForDocumentProvider(ignorePreconditionFailed: Boolean = false) {
        when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    // TODO edit mount
                    val intent = Intent(ourContext, WebdavMountsActivity::class.java)
                    throw AuthenticationRequiredException(
                        this,
                        TaskStackBuilder.create(ourContext)
                            .addNextIntentWithParentStack(intent)
                            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                }
            }
            HttpURLConnection.HTTP_NOT_FOUND ->
                throw FileNotFoundException()
            HttpURLConnection.HTTP_PRECON_FAILED ->
                if (ignorePreconditionFailed)
                    return
        }

        // re-throw
        throw this
    }

}