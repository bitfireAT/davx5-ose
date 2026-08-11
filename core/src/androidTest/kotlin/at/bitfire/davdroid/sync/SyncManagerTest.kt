/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.davdroid.MockEngineQueue
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.TestUtils.assertWithin
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.SyncState
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /**
     * Enqueues a Multi-Status response for a PROPFIND on a single resource, as sent when DAVx5 checks
     * whether the target of a failed conditional upload is still there.
     */
    private fun enqueuePropfindETag(href: String, eTag: String) {
        mockEngineQueue.enqueue(
            HttpStatusCode.MultiStatus,
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<multistatus xmlns=\"DAV:\">\n" +
                    "  <response>\n" +
                    "    <href>$href</href>\n" +
                    "    <propstat>\n" +
                    "      <prop>\n" +
                    "        <getetag>$eTag</getetag>\n" +
                    "      </prop>\n" +
                    "      <status>HTTP/1.1 200 OK</status>\n" +
                    "    </propstat>\n" +
                    "  </response>\n" +
                    "</multistatus>",
            headersOf(HttpHeaders.ContentType, "text/xml")
        )
    }

    /** All PUT requests the mock engine has received, in order. */
    private fun putRequests() =
        mockEngineQueue.engine.requestHistory.filter { it.method == HttpMethod.Put }

    /** All requests the mock engine has received for the given path (i.e. not the collection itself). */
    private fun requestsFor(encodedPath: String) =
        mockEngineQueue.engine.requestHistory.filter { it.url.encodedPath == encodedPath }

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
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed_ResourceStillThere() = runTest {
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

        // PROPFIND -> resource is still there, so it's a real edit conflict
        enqueuePropfindETag("/existing-file.txt", "changed-etag-from-server")

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

        // real edit conflict: must not be uploaded a second time (would overwrite the server version)
        assertEquals(1, putRequests().size)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed_ResourceGone() = runTest {
        // Some servers (like DAViCal) answer 412 instead of 404 when the resource is not there anymore,
        // because a conditional request with If-Match must fail on a non-existing resource.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "etag-of-the-resource-that-has-been-deleted-on-the-server"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 412 Precondition Failed
        mockEngineQueue.enqueue(HttpStatusCode.PreconditionFailed)

        // PROPFIND -> 404 Not Found, so the resource is really gone
        mockEngineQueue.enqueue(HttpStatusCode.NotFound)

        // PUT (as new resource) -> 201 Created
        mockEngineQueue.enqueue(HttpStatusCode.Created, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                Pair(
                    Response(
                        Url("$BASE_URL/"),
                        Url("$BASE_URL/existing-file.txt"),
                        null,
                        listOf(
                            PropStat(
                                listOf(
                                    GetETag("etag-from-put")
                                ),
                                HttpStatusCode.OK
                            )
                        )
                    ), HrefRelation.MEMBER
                )
            )
        }
        syncManager.performSync()

        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertEquals(1, collection.entries.size)
        assertFalse(collection.entries.first().dirty)
        assertEquals("etag-from-put", collection.entries.first().eTag)

        // first PUT is conditional, second one creates the resource again
        val puts = putRequests()
        assertEquals(2, puts.size)
        assertEquals(
            "\"etag-of-the-resource-that-has-been-deleted-on-the-server\"",
            puts[0].headers[HttpHeaders.IfMatch]
        )
        assertNull(puts[1].headers[HttpHeaders.IfMatch])
        assertEquals("*", puts[1].headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun testPerformSync_UploadModifiedMember_409Conflict_ResourceGone() = runTest {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "etag-of-the-resource-that-has-been-deleted-on-the-server"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 409 Conflict
        mockEngineQueue.enqueue(HttpStatusCode.Conflict)

        // PROPFIND -> 404 Not Found, so the resource is really gone
        mockEngineQueue.enqueue(HttpStatusCode.NotFound)

        // PUT (as new resource) -> 201 Created
        mockEngineQueue.enqueue(HttpStatusCode.Created, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                Pair(
                    Response(
                        Url("$BASE_URL/"),
                        Url("$BASE_URL/existing-file.txt"),
                        null,
                        listOf(
                            PropStat(
                                listOf(
                                    GetETag("etag-from-put")
                                ),
                                HttpStatusCode.OK
                            )
                        )
                    ), HrefRelation.MEMBER
                )
            )
        }
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError())
        assertEquals(1, collection.entries.size)
        assertFalse(collection.entries.first().dirty)
        assertEquals("etag-from-put", collection.entries.first().eTag)
        assertEquals(2, putRequests().size)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed_ResourceStateUnknown() = runTest {
        // When we can't determine whether the resource is still there, the safe behavior is kept:
        // ignore the failed upload and let the resource be downloaded again.
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

        // PROPFIND -> 500 Internal Server Error, so we don't know whether the resource is still there
        mockEngineQueue.enqueue(HttpStatusCode.InternalServerError)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                Pair(
                    Response(
                        Url("$BASE_URL/"),
                        Url("$BASE_URL/existing-file.txt"),
                        null,
                        listOf(
                            PropStat(
                                listOf(
                                    GetETag("changed-etag-from-server")
                                ),
                                HttpStatusCode.OK
                            )
                        )
                    ), HrefRelation.MEMBER
                )
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/existing-file.txt"), "changed-etag-from-server"))
        }
        syncManager.performSync()

        assertTrue(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertEquals(1, collection.entries.size)
        assertEquals("changed-etag-from-server", collection.entries.first().eTag)
        assertEquals(1, putRequests().size)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed_ResourceReappeared() = runTest {
        // The resource is gone when we ask, but back again when we upload it as a new resource.
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

        // PROPFIND -> 404 Not Found
        mockEngineQueue.enqueue(HttpStatusCode.NotFound)

        // PUT (as new resource) -> 412 Precondition Failed, too (If-None-Match didn't match)
        mockEngineQueue.enqueue(HttpStatusCode.PreconditionFailed)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                Pair(
                    Response(
                        Url("$BASE_URL/"),
                        Url("$BASE_URL/existing-file.txt"),
                        null,
                        listOf(
                            PropStat(
                                listOf(
                                    GetETag("changed-etag-from-server")
                                ),
                                HttpStatusCode.OK
                            )
                        )
                    ), HrefRelation.MEMBER
                )
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/existing-file.txt"), "changed-etag-from-server"))
        }
        syncManager.performSync()

        assertTrue(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertEquals("changed-etag-from-server", collection.entries.first().eTag)

        // the server is only asked once; the retry must not ask again (no endless recursion)
        assertEquals(2, putRequests().size)
        assertEquals(3, requestsFor("/existing-file.txt").size)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_404NotFound_UploadsAsNew() = runTest {
        // Servers which answer 404 (instead of 412) don't have to be asked whether the resource is there.
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "etag-of-the-resource-that-has-been-deleted-on-the-server"
                dirty = true
            }
        }
        enqueueQueryCapabilities("ctag1")

        // PUT -> 404 Not Found
        mockEngineQueue.enqueue(HttpStatusCode.NotFound)

        // PUT (as new resource) -> 201 Created
        mockEngineQueue.enqueue(HttpStatusCode.Created, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                Pair(
                    Response(
                        Url("$BASE_URL/"),
                        Url("$BASE_URL/existing-file.txt"),
                        null,
                        listOf(
                            PropStat(
                                listOf(
                                    GetETag("etag-from-put")
                                ),
                                HttpStatusCode.OK
                            )
                        )
                    ), HrefRelation.MEMBER
                )
            )
        }
        syncManager.performSync()

        assertFalse(syncManager.syncResult.hasError())
        assertFalse(collection.entries.first().dirty)
        assertEquals("etag-from-put", collection.entries.first().eTag)

        // two PUTs and nothing else: no PROPFIND needed to find out that the resource is gone
        assertEquals(2, putRequests().size)
        assertEquals(2, requestsFor("/existing-file.txt").size)
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
