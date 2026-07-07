/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.dav4jvm.ktor.PropStat
import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.Response.HrefRelation
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.TestUtils.assertWithin
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.settings.AccountManagerSettingsStore
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
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
    lateinit var accountSettingsFactory: AccountManagerSettingsStore.Factory

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncManagerFactory: TestSyncManager.Factory

    @BindValue
    @RelaxedMockK
    lateinit var syncStatsRepository: DavSyncStatsRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private lateinit var account: Account
    private lateinit var client: HttpClient

    private data class QueuedResponse(
        val status: HttpStatusCode,
        val body: String = "",
        val headers: Headers = headersOf()
    )
    private val responseQueue = ArrayDeque<QueuedResponse>()
    private val capturedUrls = mutableListOf<Url>()

    private fun buildMockEngine() = MockEngine { request ->
        capturedUrls += request.url
        val queued = responseQueue.removeFirstOrNull()
            ?: return@MockEngine respond("Unexpected request", HttpStatusCode.InternalServerError)
        respond(queued.body, queued.status, queued.headers)
    }

    private fun enqueue(status: HttpStatusCode, body: String = "", headers: Headers = headersOf()) {
        responseQueue.addLast(QueuedResponse(status, body, headers))
    }

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
        enqueue(
            HttpStatusCode.MultiStatus,
            body.toString(),
            headersOf(HttpHeaders.ContentType, "text/xml")
        )
    }

    @Before
    fun setUp() {
        hiltRule.inject()

        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()

        client = HttpClient(buildMockEngine())
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)

        // clear annoying syncError notifications
        NotificationManagerCompat.from(context).cancelAll()

        client.close()
    }


    @Test
    fun testPerformSync_503RetryAfter_DelaySeconds() = runTest {
        enqueue(HttpStatusCode.ServiceUnavailable, headers = headersOf(HttpHeaders.RetryAfter, "60"))

        val result = SyncResult()
        val syncManager = syncManager(LocalTestCollection(), result)
        syncManager.performSync()

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

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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
        enqueue(HttpStatusCode.NoContent, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/generated-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"etag-from-put\"")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )
        }
        syncManager.performSync()

        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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
        enqueue(HttpStatusCode.NoContent, headers = headersOf(HttpHeaders.ETag, "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/existing-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("etag-from-put")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/existing-file.txt"), "etag-from-put"))
        }
        syncManager.performSync()

        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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
        enqueue(HttpStatusCode.NoContent)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag2")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/existing-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("etag-from-propfind")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/existing-file.txt"), "etag-from-propfind"))
        }
        syncManager.performSync()

        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertTrue(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertEquals(1, collection.entries.size)
        assertEquals("etag-from-propfind", collection.entries.first().eTag)
    }

    @Test
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed() = runTest {
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
        enqueue(HttpStatusCode.PreconditionFailed)

        // modifications sent, so DAVx5 will query CTag again
        enqueueQueryCapabilities("ctag1")

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/existing-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("changed-etag-from-server")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/existing-file.txt"), "changed-etag-from-server"))
        }
        syncManager.performSync()

        assertTrue(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertTrue(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertEquals(1, collection.entries.size)
        assertEquals("changed-etag-from-server", collection.entries.first().eTag)
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
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/downloaded-member.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"MemberETag1\"")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )

        }
        syncManager.performSync()

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/new-member.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"NewMemberETag1\"")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/new-member.txt"), "NewMemberETag1"))
        }
        syncManager.performSync()

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertTrue(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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
            listAllRemoteResult = listOf(
                    Pair(Response(
                            Url("$BASE_URL/"),
                            Url("$BASE_URL/downloaded-member.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"MemberETag2\"")
                                    ),
                                    HttpStatusCode.OK
                            ))
                    ), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(Url("$BASE_URL/downloaded-member.txt"), "MemberETag2"))
        }
        syncManager.performSync()

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertTrue(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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

        assertFalse(syncManager.didGenerateUpload)
        assertFalse(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
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
        enqueue(HttpStatusCode.NoContent)           // DELETE response
        enqueueQueryCapabilities("ctag1")           // querySyncState after modifications

        val syncManager = syncManager(collection)
        syncManager.performSync()

        // The DELETE request URL must encode the slash as %2F (not split the path).
        val resourceUrl = capturedUrls.first { it.encodedPath != "/" }
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
        enqueue(HttpStatusCode.NoContent)           // PUT response
        enqueueQueryCapabilities("ctag1")           // querySyncState after modifications

        val syncManager = syncManager(collection)
        syncManager.performSync()

        // The PUT request URL must encode the slash as %2F (not split the path).
        val resourceUrl = capturedUrls.first { it.encodedPath != "/" }
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
        account,
        client,
        syncResult,
        localCollection,
        collection
    )

}
