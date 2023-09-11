/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.ThumbnailUtils
import android.net.ConnectivityManager
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract.*
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import at.bitfire.dav4jvm.DavCollection
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.*
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.network.MemoryCookieStore
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.WebDavDocument
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.webdav.WebdavMountsActivity
import at.bitfire.davdroid.webdav.cache.HeadResponseCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.util.concurrent.*
import java.util.logging.Level
import kotlin.math.min

/**
 * Provides functionality on WebDav documents.
 *
 * Actual implementation should go into [DavDocumentsActor].
 */
class DavDocumentsProvider: DocumentsProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DavDocumentsProviderEntryPoint {
        fun appDatabase(): AppDatabase
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
        const val THUMBNAIL_TIMEOUT = 15L

        fun notifyMountsChanged(context: Context) {
            context.contentResolver.notifyChange(buildRootsUri(context.getString(R.string.webdav_authority)), null)
        }
    }

    val ourContext by lazy { context!! }        // requireContext() requires API level 30
    val authority by lazy { ourContext.getString(R.string.webdav_authority) }

    private val db by lazy { EntryPointAccessors.fromApplication(ourContext, DavDocumentsProviderEntryPoint::class.java).appDatabase() }
    private val mountDao by lazy { db.webDavMountDao() }
    private val documentDao by lazy { db.webDavDocumentDao() }

    private val credentialsStore by lazy { CredentialsStore(ourContext) }
    private val cookieStore by lazy { mutableMapOf<Long, CookieJar>() }
    private val headResponseCache by lazy { HeadResponseCache() }
    private val thumbnailCache by lazy { ThumbnailCache(ourContext) }

    private val connectivityManager by lazy { ourContext.getSystemService<ConnectivityManager>()!! }
    private val storageManager by lazy { ourContext.getSystemService<StorageManager>()!! }

    private val executor by lazy {
        ThreadPoolExecutor(1, min(Runtime.getRuntime().availableProcessors(), 4), 30, TimeUnit.SECONDS, BlockingLifoQueue())
    }
    /** List of currently active [queryChildDocuments] runners.
     *
     *  Key: document ID (directory) for which children are listed.
     *  Value: whether the runner is still running (*true*) or has already finished (*false*).
     */
    private val runningQueryChildren = ConcurrentHashMap<Long, Boolean>()

    private val actor by lazy { DavDocumentsActor(ourContext, db, cookieStore, credentialsStore, authority) }

    override fun onCreate() = true

    override fun shutdown() {
        executor.shutdown()
    }


    /*** query ***/

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Logger.log.fine("WebDAV queryRoots")
        val roots = MatrixCursor(projection ?: arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_SUMMARY
        ))

        for (mount in mountDao.getAll()) {
            val rootDocument = documentDao.getOrCreateRoot(mount)
            Logger.log.info("Root ID: $rootDocument")

            roots.newRow().apply {
                add(Root.COLUMN_ROOT_ID, mount.id)
                add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                add(Root.COLUMN_TITLE, ourContext.getString(R.string.webdav_provider_root_title))
                add(Root.COLUMN_DOCUMENT_ID, rootDocument.id.toString())
                add(Root.COLUMN_SUMMARY, mount.name)
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)

                val quotaAvailable = rootDocument.quotaAvailable
                if (quotaAvailable != null)
                    add(Root.COLUMN_AVAILABLE_BYTES, quotaAvailable)

                val quotaUsed = rootDocument.quotaUsed
                if (quotaAvailable != null && quotaUsed != null)
                    add(Root.COLUMN_CAPACITY_BYTES, quotaAvailable + quotaUsed)
            }
        }

        return roots
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        Logger.log.fine("WebDAV queryDocument $documentId ${projection?.joinToString("+")}")

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val parent = doc.parentId?.let { parentId ->
            documentDao.get(parentId)
        }

        return DocumentsCursor(projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_ICON,
            Document.COLUMN_SUMMARY
        )).apply {
            val bundle = doc.toBundle(parent)
            Logger.log.fine("queryDocument($documentId) = $bundle")

            // override display names of root documents
            if (parent == null) {
                val mount = mountDao.getById(doc.mountId)
                bundle.putString(Document.COLUMN_DISPLAY_NAME, mount.name)
            }

            addRow(bundle)
        }
    }

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
        Logger.log.fine("WebDAV queryChildDocuments $parentDocumentId $projection $sortOrder")
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
            executor.submit {
                actor.queryChildren(parent)
                // Once the query is done, set query as finished (not running)
                runningQueryChildren.put(parentId, false)
                // .. and notify - effectively calling this method again
                ourContext.contentResolver.notifyChange(notificationUri, null)
            }
            true
        }

        if (running)        // worker still running
            result.loading = true
        else                // remove worker from list if done
            runningQueryChildren.remove(parentId)

        // Regardless of whether the worker is done, return the children we already have
        for (child in documentDao.getChildren(parentId)) {
            val bundle = child.toBundle(parent)
            result.addRow(bundle)
        }

        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        Logger.log.fine("WebDAV isChildDocument $parentDocumentId $documentId")
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

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        Logger.log.fine("WebDAV copyDocument $sourceDocumentId $targetParentDocumentId")
        val srcDoc = documentDao.get(sourceDocumentId.toLong()) ?: throw FileNotFoundException()
        val dstFolder = documentDao.get(targetParentDocumentId.toLong()) ?: throw FileNotFoundException()
        val name = srcDoc.name

        if (srcDoc.mountId != dstFolder.mountId)
            throw UnsupportedOperationException("Can't COPY between WebDAV servers")

        val dstDocId: String
        actor.httpClient(srcDoc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, srcDoc.toHttpUrl(db))
            try {
                val dstUrl = dstFolder.toHttpUrl(db).newBuilder()
                    .addPathSegment(name)
                    .build()
                dav.copy(dstUrl, false) {
                    // successfully copied
                }

                dstDocId = documentDao.insertOrReplace(WebDavDocument(
                    mountId = dstFolder.mountId,
                    parentId = dstFolder.id,
                    name = name,
                    isDirectory = srcDoc.isDirectory,
                    displayName = srcDoc.displayName,
                    mimeType = srcDoc.mimeType,
                    size = srcDoc.size
                )).toString()

                actor.notifyFolderChanged(targetParentDocumentId)
            } catch (e: HttpException) {
                if (e.code == HttpURLConnection.HTTP_NOT_FOUND)
                    throw FileNotFoundException()
                throw e
            }
        }

        return dstDocId
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? {
        Logger.log.fine("WebDAV createDocument $parentDocumentId $mimeType $displayName")
        val parent = documentDao.get(parentDocumentId.toLong()) ?: throw FileNotFoundException()
        val parentUrl = parent.toHttpUrl(db)
        val createDirectory = mimeType == Document.MIME_TYPE_DIR

        var docId: Long? = null
        actor.httpClient(parent.mountId).use { client ->
            for (attempt in 0..MAX_NAME_ATTEMPTS) {
                val newName = displayNameToMemberName(displayName, attempt)
                val newLocation = parentUrl.newBuilder()
                    .addPathSegment(newName)
                    .build()
                val doc = DavResource(client.okHttpClient, newLocation)
                try {
                    if (createDirectory)
                        doc.mkCol(null) {
                            // directory successfully created
                        }
                    else
                        doc.put("".toRequestBody(null), ifNoneMatch = true) {
                            // document successfully created
                        }

                    docId = documentDao.insertOrReplace(WebDavDocument(
                        mountId = parent.mountId,
                        parentId = parent.id,
                        name = newName,
                        mimeType = mimeType.toMediaTypeOrNull(),
                        isDirectory = createDirectory
                    ))

                    actor.notifyFolderChanged(parentDocumentId)
                    break
                } catch (e: HttpException) {
                    e.throwForDocumentProvider(true)
                }
            }
        }

        return docId?.toString()
    }

    override fun deleteDocument(documentId: String) {
        Logger.log.fine("WebDAV removeDocument $documentId")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        actor.httpClient(doc.mountId).use { client ->
            val dav = DavResource(client.okHttpClient, doc.toHttpUrl(db))
            try {
                dav.delete {
                    // successfully deleted
                }
                Logger.log.fine("Successfully removed")
                documentDao.delete(doc)

                actor.notifyFolderChanged(doc.parentId)
            } catch (e: HttpException) {
                e.throwForDocumentProvider()
            }
        }
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
        Logger.log.fine("WebDAV moveDocument $sourceDocumentId $sourceParentDocumentId $targetParentDocumentId")
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
                dav.move(newLocation, false) {
                    // successfully moved
                }

                doc.parentId = dstParent.id
                documentDao.update(doc)

                actor.notifyFolderChanged(sourceParentDocumentId)
                actor.notifyFolderChanged(targetParentDocumentId)
            } catch (e: HttpException) {
                e.throwForDocumentProvider()
            }
        }

        return doc.id.toString()
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        Logger.log.fine("WebDAV renameDocument $documentId $displayName")
        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val oldUrl = doc.toHttpUrl(db)
        actor.httpClient(doc.mountId).use { client ->
            for (attempt in 0..MAX_NAME_ATTEMPTS) {
                val newName = displayNameToMemberName(displayName, attempt)
                val newLocation = oldUrl.newBuilder()
                    .removePathSegment(oldUrl.pathSegments.lastIndex)
                    .addPathSegment(newName)
                    .build()
                try {
                    val dav = DavResource(client.okHttpClient, oldUrl)
                    dav.move(newLocation, false) {
                        // successfully renamed
                    }
                    doc.name = newName
                    documentDao.update(doc)

                    actor.notifyFolderChanged(doc.parentId)
                    return doc.id.toString()
                } catch (e: HttpException) {
                    e.throwForDocumentProvider(true)
                }
            }
        }
        return null
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

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        Logger.log.fine("WebDAV openDocument $documentId $mode $signal")

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val url = doc.toHttpUrl(db)
        val client = actor.httpClient(doc.mountId)

        val modeFlags = ParcelFileDescriptor.parseMode(mode)
        val readAccess = when (mode) {
            "r" -> true
            "w", "wt" -> false
            else -> throw UnsupportedOperationException("Mode $mode not supported by WebDAV")
        }

        val fileInfo = headResponseCache.get(doc) {
            val deferredFileInfo = executor.submit(HeadInfoDownloader(client, url))
            signal?.setOnCancelListener {
                deferredFileInfo.cancel(true)
            }
            deferredFileInfo.get()
        }
        Logger.log.info("Received file info: $fileInfo")

        return if (
            Build.VERSION.SDK_INT >= 26 &&      // openProxyFileDescriptor exists since Android 8.0
            readAccess &&                       // WebDAV doesn't support random write access natively
            fileInfo.size != null &&            // file descriptor must return a useful value on getFileSize()
            (fileInfo.eTag != null || fileInfo.lastModified != null) &&     // we need a method to determine whether the document has changed during access
            fileInfo.supportsPartial != false   // WebDAV server must support random access
        ) {
            val accessor = RandomAccessCallback.Wrapper(ourContext, client, url, doc.mimeType, fileInfo, signal)
            storageManager.openProxyFileDescriptor(modeFlags, accessor, accessor.callback!!.workerHandler)
        } else {
            val fd = StreamingFileDescriptor(ourContext, client, url, doc.mimeType, signal) { transferred ->
                // called when transfer is finished

                val now = System.currentTimeMillis()
                if (!readAccess /* write access */) {
                    // write access, update file size
                    doc.size = transferred
                    doc.lastModified = now
                    documentDao.update(doc)
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
        Logger.log.info("openDocumentThumbnail documentId=$documentId sizeHint=$sizeHint signal=$signal")

        if (connectivityManager.isActiveNetworkMetered)
            // don't download the large images just to create a thumbnail on metered networks
            return null

        if (signal == null) {
            // see https://github.com/zhanghai/MaterialFiles/issues/588
            Logger.log.warning("openDocumentThumbnail without cancellationSignal causes too much problems, please fix calling app")
            return null
        }

        val doc = documentDao.get(documentId.toLong()) ?: throw FileNotFoundException()
        val thumbFile = thumbnailCache.get(doc, sizeHint) {
            // create thumbnail
            val result = executor.submit(Callable<ByteArray> {
                actor.httpClient(doc.mountId).use { client ->
                    val url = doc.toHttpUrl(db)
                    val dav = DavResource(client.okHttpClient, url)
                    var result: ByteArray? = null
                    dav.get("image/*", null) { response ->
                        response.body?.byteStream()?.use { data ->
                            BitmapFactory.decodeStream(data)?.let { bitmap ->
                                val thumb = ThumbnailUtils.extractThumbnail(bitmap, sizeHint.x, sizeHint.y)
                                val baos = ByteArrayOutputStream()
                                thumb.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                                result = baos.toByteArray()
                            }
                        }
                    }
                    result
                }
            })

            signal.setOnCancelListener {
                Logger.log.fine("Cancelling thumbnail for ${doc.name}")
                result.cancel(true)
            }

            val finalResult =
                try {
                    result.get(THUMBNAIL_TIMEOUT, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    Logger.log.warning("Couldn't generate thumbnail in time, cancelling")
                    result.cancel(true)
                    null
                } catch (e: Exception) {
                    Logger.log.log(Level.WARNING, "Couldn't generate thumbnail", e)
                    null
                }

            finalResult
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
    class DavDocumentsActor(
        private val context: Context,
        private val db: AppDatabase,
        private val cookieStore: MutableMap<Long, CookieJar>,
        private val credentialsStore: CredentialsStore,
        private val authority: String
    ) {
        private val documentDao = db.webDavDocumentDao()

        /**
         * Finds children of given parent [WebDavDocument]. After querying, it
         * updates existing children, adds new ones or removes deleted ones.
         *
         * There must never be more than one running instance per [parent]!
         *
         * @param parent    folder to search for children
         */
        @WorkerThread
        internal fun queryChildren(parent: WebDavDocument) {
            val oldChildren = documentDao.getChildren(parent.id).associateBy { it.name }.toMutableMap() // "name" of file/folder must be unique
            val newChildrenList = hashMapOf<String, WebDavDocument>()

            httpClient(parent.mountId).use { client ->
                val parentUrl = parent.toHttpUrl(db)
                val folder = DavCollection(client.okHttpClient, parentUrl)

                try {
                    folder.propfind(1, *DAV_FILE_FIELDS) { response, relation ->
                        Logger.log.fine("$relation $response")

                        val resource: WebDavDocument =
                            when (relation) {
                                Response.HrefRelation.SELF ->       // it's about the parent
                                    parent
                                Response.HrefRelation.MEMBER ->     // it's about a member
                                    WebDavDocument(mountId = parent.mountId, parentId = parent.id, name = response.hrefName())
                                else -> {
                                    // we didn't request this; log a warning and ignore it
                                    Logger.log.warning("Ignoring unexpected $response $relation in $parentUrl")
                                    return@propfind
                                }
                            }

                        response[ResourceType::class.java]?.types?.let { types ->
                            resource.isDirectory = types.contains(ResourceType.COLLECTION)
                        }

                        resource.displayName = response[DisplayName::class.java]?.displayName
                        resource.mimeType = response[GetContentType::class.java]?.type
                        response[GetETag::class.java]?.let { getETag ->
                            if (!getETag.weak)
                                resource.eTag = resource.eTag
                        }
                        resource.lastModified = response[GetLastModified::class.java]?.lastModified?.toInstant()?.toEpochMilli()
                        resource.size = response[GetContentLength::class.java]?.contentLength

                        val privs = response[CurrentUserPrivilegeSet::class.java]
                        resource.mayBind = privs?.mayBind
                        resource.mayUnbind = privs?.mayUnbind
                        resource.mayWriteContent = privs?.mayWriteContent

                        resource.quotaAvailable = response[QuotaAvailableBytes::class.java]?.quotaAvailableBytes
                        resource.quotaUsed = response[QuotaUsedBytes::class.java]?.quotaUsedBytes

                        if (resource == parent)
                            documentDao.update(resource)
                        else {
                            documentDao.insertOrUpdate(resource)
                            newChildrenList[resource.name] = resource
                        }

                        // remove resource from known child nodes, because not found on server
                        oldChildren.remove(resource.name)
                    }
                } catch (e: Exception) {
                    Logger.log.log(Level.WARNING, "Couldn't query children", e)
                }
            }

            // Delete child nodes which were not rediscovered (deleted serverside)
            for ((_, oldChild) in oldChildren)
                documentDao.delete(oldChild)
        }


        // helpers

        internal fun httpClient(mountId: Long): HttpClient {
            val builder = HttpClient.Builder(context, loggerLevel = HttpLoggingInterceptor.Level.HEADERS)
                .cookieStore(cookieStore.getOrPut(mountId) { MemoryCookieStore() })

            credentialsStore.getCredentials(mountId)?.let { credentials ->
                builder.addAuthentication(null, credentials, true)
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
                    throw AuthenticationRequiredException(this, PendingIntent.getActivity(ourContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
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