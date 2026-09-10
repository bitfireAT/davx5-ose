/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.MockEngineQueue
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.TestUtils.assertWithin
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.local.SyncState
import at.bitfire.davdroid.resource.remote.CollectionSyncItem
import at.bitfire.davdroid.resource.remote.InternalMemberState
import at.bitfire.davdroid.resource.remote.TestWebDavCollection
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class SyncManagerTest {

    companion object {
        const val BASE_URL = "https://dav.example.com"
    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncManagerFactory: TestSyncManager.Factory

    @BindValue
    @RelaxedMockK
    lateinit var syncStatsRepository: DavSyncStatsRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private lateinit var accountId: LegacyAccount
    private lateinit var client: HttpClient

    private val mockEngineQueue = MockEngineQueue()

    private fun enqueueQueryCapabilities(cTag: String? = null) {
        val body = StringBuilder()
        body.append(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<multistatus xmlns=\"DAV:\" xmlns:CALDAV=\"http://calendarserver.org/ns/\">\n" +
                    "  <response>\n" +
                    "    <href>/</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n"
        )
        if (cTag != null)
            body.append("<CALDAV:getctag>$cTag</CALDAV:getctag>\n")
        body.append(
            "      </prop>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>"
        )
        mockEngineQueue.enqueue(
            HttpStatusCode.MultiStatus,
            body.toString(),
            headersOf(HttpHeaders.ContentType, "text/xml")
        )
    }

    /** Number of `PUT` requests which have been sent to the mock engine. */
    private fun numberOfPutRequests() =
        mockEngineQueue.engine.requestHistory.count { it.method == HttpMethod.Put }


    @Before
    fun setUp() {
        hiltRule.inject()

        TestUtils.setUpWorkManager(context, workerFactory)

        accountId = LegacyAccount(TestAccount.create())

        client = HttpClient(mockEngineQueue.engine)
    }

    @After
    fun tearDown() {
        TestAccount.remove(accountId.androidAccount)

        // clear annoying syncError notifications
        NotificationManagerCompat.from(context).cancelAll()

        client.close()
    }


    @Test
    fun testPerformSync_503RetryAfter_DelaySeconds() = runTest {
        mockEngineQueue.enqueue(HttpStatusCode.ServiceUnavailable, headers = headersOf(HttpHeaders.RetryAfter, "60"))

        val result = SyncResult()
        val syncManager = syncManager(LocalTestCollection(), result)
        syncManager.performSync()

        verify(exactly = 0) { syncManager.remoteCollection.listFilteredMembers() }
        val expected = Instant.now()
            .plusSeconds(60)
            .toEpochMilli()
        // 5 sec tolerance for test
        assertWithin(expected, result.delayUntil*1000, 5000)
    }

    @Test
    fun testPerformSync_FirstSync_Empty() = runTest {
        val collection = LocalTestCollection() /* no last known ctag */
        enqueueQueryCapabilities()

        val syncManager = syncManager(collection)
        syncManager.performSync()

        coVerify(exactly = 1) { syncManager.remoteCollection.queryCapabilities() }
        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
    }

    @Test
    fun testPerformSync_UploadNewMember_ETagOnPut() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 204 No Content
        mockEngineQueue.enqueue(HttpStatusCode.NoContent, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/generated-file.txt"), "etag-from-put")
            )
        }
        syncManager.performSync()

        coVerify(exactly = 1) { syncManager.remoteCollection.queryCapabilities() }
        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("etag-from-put", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_ETagOnPut() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "old-etag-like-on-server"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 204 No Content
        mockEngineQueue.enqueue(HttpStatusCode.NoContent, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/existing-file.txt"), "etag-from-put")
            )
        }
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("etag-from-put", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_NoETagOnPut() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "old-etag-like-on-server"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 204 No Content
        mockEngineQueue.enqueue(HttpStatusCode.NoContent)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/existing-file.txt"), "etag-from-propfind")
            )
        }
        every { syncManager.remoteCollection.multiget(any(), any()) } returns flowOf(
            WebDavCollection.MultiGetItem(Url("$BASE_URL/existing-file.txt"), "etag-from-propfind", content = "ignored")
        )
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertTrue(syncManager.didGenerateUpload)
        assertEquals(
            listOf(
                WebDavCollection.MultiGetItem(
                    Url("$BASE_URL/existing-file.txt"),
                    "etag-from-propfind",
                    content = "ignored"
                )
            ),
            syncManager.processedDownloads
        )
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("etag-from-propfind", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed_ChangedOnServer() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "etag-that-has-been-changed-on-server-in-the-meanwhile"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 412 Precondition Failed
        mockEngineQueue.enqueue(HttpStatusCode.PreconditionFailed)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/existing-file.txt"), "changed-etag-from-server")
            )
        }
        every { syncManager.remoteCollection.multiget(any(), any()) } returns flowOf(
            WebDavCollection.MultiGetItem(
                Url("$BASE_URL/existing-file.txt"),
                "changed-etag-from-server",
                content = "ignored"
            )
        )
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertTrue(syncManager.didGenerateUpload)
        assertEquals(
            listOf(
                WebDavCollection.MultiGetItem(
                    Url("$BASE_URL/existing-file.txt"),
                    "changed-etag-from-server",
                    content = "ignored"
                )
            ),
            syncManager.processedDownloads
        )
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("changed-etag-from-server", collection.entries.first().eTag)
        // local change has been discarded, so the resource is not dirty anymore
        assertFalse(collection.entries.first().dirty)
        // the upload must not be retried within the same sync
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed_DeletedOnServer() = runTest {
        // The resource has been deleted on the server, so our If-Match doesn't match anymore
        // (RFC 9110 13.1.1). The local change is discarded and the resource is deleted locally.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "deleted-on-server.txt"
                eTag = "etag-of-the-resource-which-has-been-deleted-on-server"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 412 Precondition Failed
        mockEngineQueue.enqueue(HttpStatusCode.PreconditionFailed)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        // server doesn't list the resource anymore
        val syncManager = syncManager(collection)
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadModifiedMember_409Conflict() = runTest {
        // We can't resolve a conflict interactively, so 409 is treated like 412.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "deleted-on-server.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 409 Conflict
        mockEngineQueue.enqueue(HttpStatusCode.Conflict)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadModifiedMember_409Conflict_DataRejected() = runTest {
        // A 409 which reports a CalDAV/CardDAV precondition means that our data was rejected, so there's
        // no server version which could replace the local one: assert sync error and that the local change is kept.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 409 Conflict with CALDAV:no-uid-conflict precondition
        mockEngineQueue.enqueue(
            HttpStatusCode.Conflict,
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<error xmlns=\"DAV:\"><no-uid-conflict xmlns=\"urn:ietf:params:xml:ns:caldav\"/></error>",
            headersOf(HttpHeaders.ContentType, "text/xml")
        )

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertTrue(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertTrue(collection.entries.first().dirty)
        assertEquals(SyncState(SyncState.Type.CTAG, "old-ctag"), collection.lastSyncState)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_404NotFound() = runTest {
        // Servers which don't answer 412 when the resource is gone must be handled like 412,
        // especially the upload must not be retried as a new resource ("the server always wins").
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "deleted-on-server.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 404 Not Found
        mockEngineQueue.enqueue(HttpStatusCode.NotFound)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadModifiedMember_410Gone() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "deleted-on-server.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 410 Gone
        mockEngineQueue.enqueue(HttpStatusCode.Gone)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadModifiedMember_403Forbidden_NeedPrivileges() = runTest {
        // The collection is effectively read-only for us, so the local change is discarded.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "not-writable.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 403 Forbidden with DAV:need-privileges precondition
        mockEngineQueue.enqueue(
            HttpStatusCode.Forbidden,
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<error xmlns=\"DAV:\"><need-privileges/></error>",
            headersOf(HttpHeaders.ContentType, "text/xml")
        )

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadModifiedMember_403Forbidden_Other() = runTest {
        // A 403 which is not caused by missing permissions is a sync error; the local change is kept.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 403 Forbidden without further information
        mockEngineQueue.enqueue(HttpStatusCode.Forbidden)

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertTrue(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertTrue(collection.entries.first().dirty)
    }

    @Test
    fun testPerformSync_UploadNewMember_412PreconditionFailed() = runTest {
        // A new resource is uploaded with If-None-Match: *, so 412 means that the file name (which is
        // derived from the UID) is already taken on the server. The local change is discarded and the
        // server's version is downloaded instead, so that there's exactly one local resource afterwards.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 412 Precondition Failed
        mockEngineQueue.enqueue(HttpStatusCode.PreconditionFailed)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/generated-file.txt"), "etag-from-server")
            )
        }
        every { syncManager.remoteCollection.multiget(any(), any()) } returns flowOf(
            WebDavCollection.MultiGetItem(
                Url("$BASE_URL/generated-file.txt"),
                "etag-from-server",
                content = "ignored"
            )
        )
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        collection.entries.first().let { entry ->
            assertEquals("generated-file.txt", entry.fileName)
            assertEquals("etag-from-server", entry.eTag)
            assertFalse(entry.dirty)
        }
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_UploadNewMember_404NotFound() = runTest {
        // 404 when creating a new resource means that the collection itself is not there (anymore),
        // which is a sync error. The local resource must be kept (and stay dirty).
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 404 Not Found
        mockEngineQueue.enqueue(HttpStatusCode.NotFound)

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertTrue(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertTrue(collection.entries.first().dirty)
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_CollectionSync_UploadModifiedMember_403Forbidden_NeedPrivileges() = runTest {
        // The resource itself hasn't been changed on the server, so a sync-collection REPORT wouldn't
        // report it. Discarding the local change must force a full re-listing, otherwise the local
        // version would never be overwritten by the server's version.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.SYNC_TOKEN, "token1")
            entries += LocalTestResource().apply {
                fileName = "not-writable.txt"
                eTag = "some-etag"
                dirty = true
            }
        }
        enqueueQueryCapabilities()

        // PUT -> 403 Forbidden with DAV:need-privileges precondition
        mockEngineQueue.enqueue(
            HttpStatusCode.Forbidden,
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<error xmlns=\"DAV:\"><need-privileges/></error>",
            headersOf(HttpHeaders.ContentType, "text/xml")
        )

        val syncManager = syncManager(collection).apply {
            chosenSyncAlgorithm = SyncManager.SyncAlgorithm.COLLECTION_SYNC
        }
        // server doesn't report any changes
        every { syncManager.remoteCollection.listChanges(any()) } returns flowOf(
            CollectionSyncItem.SyncToken(SyncToken("token2"))
        )
        syncManager.performSync()

        // initial sync (= full listing), because the sync state has been reset
        verify(exactly = 1) { syncManager.remoteCollection.listChanges(null) }
        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_CollectionSync_UploadNewMember_412PreconditionFailed() = runTest {
        // The resource which is already on the server may be older than our sync-token, so a
        // sync-collection REPORT wouldn't report it. Discarding the local change must force a full
        // re-listing, otherwise the local resource would stay behind forever (it has no file name, so
        // it's neither uploaded again nor recognized as a member of the collection).
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.SYNC_TOKEN, "token1")
            entries += LocalTestResource().apply {
                dirty = true
            }
        }
        enqueueQueryCapabilities()

        // PUT -> 412 Precondition Failed
        mockEngineQueue.enqueue(HttpStatusCode.PreconditionFailed)

        val syncManager = syncManager(collection).apply {
            chosenSyncAlgorithm = SyncManager.SyncAlgorithm.COLLECTION_SYNC
        }
        // server doesn't report any changes
        every { syncManager.remoteCollection.listChanges(any()) } returns flowOf(
            CollectionSyncItem.SyncToken(SyncToken("token2"))
        )
        syncManager.performSync()

        // initial sync (= full listing), because the sync state has been reset
        verify(exactly = 1) { syncManager.remoteCollection.listChanges(null) }
        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
        assertEquals(1, numberOfPutRequests())
    }

    @Test
    fun testPerformSync_NoopOnMemberWithSameETag() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
            entries += LocalTestResource().apply {
                fileName = "downloaded-member.txt"
                eTag = "MemberETag1"
            }
        }
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/downloaded-member.txt"), "MemberETag1")
            )
        }
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("MemberETag1", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_DownloadNewMember() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
        }
        enqueueQueryCapabilities(cTag = "new-ctag")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/new-member.txt"), "NewMemberETag1")
            )
        }
        every { syncManager.remoteCollection.multiget(any(), any()) } returns flowOf(
            WebDavCollection.MultiGetItem(Url("$BASE_URL/new-member.txt"), "NewMemberETag1", content = "ignored")
        )
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertFalse(syncManager.didGenerateUpload)
        assertEquals(
            listOf(
                WebDavCollection.MultiGetItem(
                    Url("$BASE_URL/new-member.txt"),
                    "NewMemberETag1",
                    content = "ignored"
                )
            ),
            syncManager.processedDownloads
        )
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("NewMemberETag1", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_DownloadUpdatedMember() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "downloaded-member.txt"
                eTag = "MemberETag1"
            }
        }
        enqueueQueryCapabilities(cTag = "new-ctag")

        val syncManager = syncManager(collection).apply {
            (remoteCollection as TestWebDavCollection).listFilteredMembersResult = listOf(
                InternalMemberState(Url("$BASE_URL/downloaded-member.txt"), "MemberETag2")
            )
        }
        every { syncManager.remoteCollection.multiget(any(), any()) } returns flowOf(
            WebDavCollection.MultiGetItem(Url("$BASE_URL/downloaded-member.txt"), "MemberETag2", content = "ignored")
        )
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertFalse(syncManager.didGenerateUpload)
        assertEquals(
            listOf(
                WebDavCollection.MultiGetItem(
                    Url("$BASE_URL/downloaded-member.txt"),
                    "MemberETag2",
                    content = "ignored"
                )
            ),
            syncManager.processedDownloads
        )
        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertEquals("MemberETag2", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_RemoveVanishedMember() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "downloaded-member.txt"
            }
        }
        enqueueQueryCapabilities(cTag = "new-ctag")

        val syncManager = syncManager(collection)
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
    }

    @Test
    fun testPerformSync_KeepVanishedMemberWhichBecameDirtyDuringSync() = runTest {
        // A resource which is created locally *while* the sync is running has never had a chance to be
        // uploaded, so it must not be deleted by deleteNotPresentRemotely() although the server didn't
        // list it. This is why resetPresentRemotely()/deleteNotPresentRemotely() ignore dirty entries.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
        }
        enqueueQueryCapabilities(cTag = "new-ctag")

        val syncManager = syncManager(collection)
        every { syncManager.remoteCollection.listFilteredMembers() } returns flow {
            // user creates a local resource while the (empty) remote listing is being processed
            collection.entries += LocalTestResource().apply {
                dirty = true
            }
        }
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError)
        assertEquals(1, collection.entries.size)
        assertTrue(collection.entries.first().dirty)
    }

    @Test
    fun testPerformSync_CTagDidntChange() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
        }
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection)
        syncManager.performSync()

        verify(exactly = 0) { syncManager.remoteCollection.listFilteredMembers() }
        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.processedDownloads.isEmpty())
        assertFalse(syncManager.syncResult.hasError)
        assertTrue(collection.entries.isEmpty())
    }


    @Test
    fun testDeleteLocally_SlashInFileName_SlashEncoded() = runTest {
        // Filename containing a literal slash — must be encoded as %2F, not treated as a path separator.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
            entries += LocalTestResource().apply {
                fileName = "has/slash.ics"
                deleted = true
            }
        }
        enqueueQueryCapabilities("ctag1")
        mockEngineQueue.enqueue(HttpStatusCode.NoContent)   // DELETE response
        enqueueQueryCapabilities("ctag1")                   // querySyncState after modifications

        val syncManager = syncManager(collection)
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        // The DELETE request URL must encode the slash as %2F (not split the path).
        val resourceUrl = mockEngineQueue.engine.requestHistory.first { it.url.encodedPath != "/" }.url
        assertEquals("/has%2Fslash.ics", resourceUrl.encodedPath)
    }

    @Test
    fun testUploadDirty_SlashInFileName_SlashEncoded() = runTest {
        // Filename containing a literal slash — must be encoded as %2F, not treated as a path separator.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
            entries += LocalTestResource().apply {
                fileName = "has/slash.ics"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")
        mockEngineQueue.enqueue(HttpStatusCode.NoContent)   // PUT response
        enqueueQueryCapabilities("ctag1")                   // querySyncState after modifications

        val syncManager = syncManager(collection)
        syncManager.performSync()

        verify(exactly = 1) { syncManager.remoteCollection.listFilteredMembers() }
        // The PUT request URL must encode the slash as %2F (not split the path).
        val resourceUrl = mockEngineQueue.engine.requestHistory.first { it.url.encodedPath != "/" }.url
        assertEquals("/has%2Fslash.ics", resourceUrl.encodedPath)
    }


    // helpers

    private fun syncManager(
        localCollection: LocalTestCollection,
        syncResult: SyncResult = SyncResult(),
        collection: Collection = mockk<Collection>(relaxed = true) {
            every { id } returns 1
            every { url } returns Url("$BASE_URL/")
        }
    ) = syncManagerFactory.create(
        accountId,
        client,
        syncResult,
        localCollection,
        collection,
        spyk(TestWebDavCollection(client, collection.url)),
        SyncSettingsFixtures.default()
    )

}
