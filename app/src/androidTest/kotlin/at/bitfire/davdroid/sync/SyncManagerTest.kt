/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.dav4jvm.PropStat
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.Response.HrefRelation
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.TestUtils.assertWithin
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavSyncStatsRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.internal.http.StatusLine
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var syncManagerFactory: TestSyncManager.Factory

    @BindValue
    @MockK(relaxed = true)
    lateinit var syncStatsRepository: DavSyncStatsRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockKRule = MockKRule(this)

    private lateinit var account: Account
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()

        server = MockWebServer().apply {
            start()
        }
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)

        // clear annoying syncError notifications
        NotificationManagerCompat.from(context).cancelAll()

        server.close()
    }


    @Test
    fun testGetDelayUntil_defaultOnNull() {
        val now = Instant.now()
        val delayUntil = SyncManager.getDelayUntil(null).epochSecond
        val default = now.plusSeconds(SyncManager.DELAY_UNTIL_DEFAULT).epochSecond
        assertWithin(default, delayUntil, 5)
    }

    @Test
    fun testGetDelayUntil_reducesToMax() {
        val now = Instant.now()
        val delayUntil = SyncManager.getDelayUntil(now.plusSeconds(10*24*60*60)).epochSecond
        val max = now.plusSeconds(SyncManager.DELAY_UNTIL_MAX).epochSecond
        assertWithin(max, delayUntil, 5)
    }

    @Test
    fun testGetDelayUntil_increasesToMin() {
        val delayUntil = SyncManager.getDelayUntil(Instant.EPOCH).epochSecond
        val min = Instant.now().plusSeconds(SyncManager.DELAY_UNTIL_MIN).epochSecond
        assertWithin(min, delayUntil, 5)
    }


    private fun queryCapabilitiesResponse(cTag: String? = null): MockResponse {
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
        return MockResponse()
            .setResponseCode(207)
            .setHeader("Content-Type", "text/xml")
            .setBody(body.toString())
    }

    @Test
    fun testPerformSync_503RetryAfter_DelaySeconds() {
        server.enqueue(MockResponse()
            .setResponseCode(503)
            .setHeader("Retry-After", "60"))    // 60 seconds

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
    fun testPerformSync_FirstSync_Empty() {
        val collection = LocalTestCollection() /* no last known ctag */
        server.enqueue(queryCapabilitiesResponse())

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertTrue(collection.entries.isEmpty())
    }

    @Test
    fun testPerformSync_UploadNewMember_ETagOnPut() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                dirty = true
            }
        }
        server.enqueue(queryCapabilitiesResponse("ctag1"))

        // PUT -> 204 No Content
        server.enqueue(MockResponse()
                .setResponseCode(204)
                .setHeader("ETag", "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        server.enqueue(queryCapabilitiesResponse("ctag2"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/generated-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"etag-from-put\"")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                            )), HrefRelation.MEMBER)
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
    fun testPerformSync_UploadModifiedMember_ETagOnPut() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "old-etag-like-on-server"
                dirty = true
            }
        }
        server.enqueue(queryCapabilitiesResponse("ctag1"))

        // PUT -> 204 No Content
        server.enqueue(MockResponse()
                .setResponseCode(204)
                .addHeader("ETag", "etag-from-put"))

        // modifications sent, so DAVx5 will query CTag again
        server.enqueue(queryCapabilitiesResponse("ctag2"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/existing-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("etag-from-put")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                            )), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(server.url("/existing-file.txt"), "etag-from-put"))
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
    fun testPerformSync_UploadModifiedMember_NoETagOnPut() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "old-etag-like-on-server"
                dirty = true
            }
        }
        server.enqueue(queryCapabilitiesResponse("ctag1"))

        // PUT -> 204 No Content
        server.enqueue(MockResponse().setResponseCode(204))

        // modifications sent, so DAVx5 will query CTag again
        server.enqueue(queryCapabilitiesResponse("ctag2"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/existing-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("etag-from-propfind")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                            )), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(server.url("/existing-file.txt"), "etag-from-propfind"))
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
    fun testPerformSync_UploadModifiedMember_412PreconditionFailed() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "existing-file.txt"
                eTag = "etag-that-has-been-changed-on-server-in-the-meanwhile"
                dirty = true
            }
        }
        server.enqueue(queryCapabilitiesResponse("ctag1"))

        // PUT -> 412 Precondition Failed
        server.enqueue(MockResponse()
                .setResponseCode(412))

        // modifications sent, so DAVx5 will query CTag again
        server.enqueue(queryCapabilitiesResponse("ctag1"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/existing-file.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("changed-etag-from-server")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                            )), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(server.url("/existing-file.txt"), "changed-etag-from-server"))
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
    fun testPerformSync_NoopOnMemberWithSameETag() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
            entries += LocalTestResource().apply {
                fileName = "downloaded-member.txt"
                eTag = "MemberETag1"
            }
        }
        server.enqueue(queryCapabilitiesResponse("ctag2"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/downloaded-member.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"MemberETag1\"")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                            )), HrefRelation.MEMBER)
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
    fun testPerformSync_DownloadNewMember() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
        }
        server.enqueue(queryCapabilitiesResponse(cTag = "new-ctag"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/new-member.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"NewMemberETag1\"")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                    )), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(server.url("/new-member.txt"), "NewMemberETag1"))
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
    fun testPerformSync_DownloadUpdatedMember() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "downloaded-member.txt"
                eTag = "MemberETag1"
            }
        }
        server.enqueue(queryCapabilitiesResponse(cTag = "new-ctag"))

        val syncManager = syncManager(collection).apply {
            listAllRemoteResult = listOf(
                    Pair(Response(
                            server.url("/"),
                            server.url("/downloaded-member.txt"),
                            null,
                            listOf(PropStat(
                                    listOf(
                                            GetETag("\"MemberETag2\"")
                                    ),
                                    StatusLine(Protocol.HTTP_1_1, 200, "OK")
                            )
                            )), HrefRelation.MEMBER)
            )

            assertDownloadRemote = mapOf(Pair(server.url("/downloaded-member.txt"), "MemberETag2"))
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
    fun testPerformSync_RemoveVanishedMember() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "old-ctag")
            entries += LocalTestResource().apply {
                fileName = "downloaded-member.txt"
            }
        }
        server.enqueue(queryCapabilitiesResponse(cTag = "new-ctag"))

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.didGenerateUpload)
        assertTrue(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertTrue(collection.entries.isEmpty())
    }

    @Test
    fun testPerformSync_CTagDidntChange() {
        val collection = LocalTestCollection().apply {
            lastSyncState = SyncState(SyncState.Type.CTAG, "ctag1")
        }
        server.enqueue(queryCapabilitiesResponse("ctag1"))

        val syncManager = syncManager(collection)
        syncManager.performSync()

        assertFalse(syncManager.didGenerateUpload)
        assertFalse(syncManager.didListAllRemote)
        assertFalse(syncManager.didDownloadRemote)
        assertFalse(syncManager.syncResult.hasError())
        assertTrue(collection.entries.isEmpty())
    }


    // helpers

    private fun syncManager(
        localCollection: LocalTestCollection,
        syncResult: SyncResult = SyncResult(),
        collection: Collection = mockk<Collection>() {
            every { id } returns 1
            every { url } returns server.url("/")
        }
    ) = syncManagerFactory.create(
        account,
        arrayOf(),
        "TestAuthority",
        httpClientBuilder.build(),
        syncResult,
        localCollection,
        collection
    )

}