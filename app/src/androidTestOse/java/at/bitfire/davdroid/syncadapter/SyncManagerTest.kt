/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.SyncResult
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.dav4jvm.PropStat
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.Response.HrefRelation
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.Protocol
import okhttp3.internal.http.StatusLine
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
class SyncManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }

    companion object {

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = Account("SyncManagerTest", context.getString(R.string.account_type))

        @BeforeClass
        @JvmStatic
        fun createAccount() {
            assertTrue(AccountManager.get(context).addAccountExplicitly(account, "test", AccountSettings.initialUserData(Credentials("test", "test"))))
        }

        @AfterClass
        @JvmStatic
        fun removeAccount() {
            assertTrue(AccountManager.get(context).removeAccount(account, null, null).getResult(10, TimeUnit.SECONDS))

            // clear annoying syncError notifications
            NotificationManagerCompat.from(context).cancelAll()
        }

    }

    val server = MockWebServer()

    private fun syncManager(collection: LocalTestCollection) =
            TestSyncManager(
                    context,
                    account,
                    Bundle(),
                    "TestAuthority",
                    HttpClient.Builder().build(),
                    SyncResult(),
                    collection,
                    server
            )

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }


    private fun queryCapabilitiesResponse(cTag: String? = null): MockResponse {
        val body = StringBuilder()
        body.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<multistatus xmlns=\"DAV:\" xmlns:CALDAV=\"http://calendarserver.org/ns/\">\n" +
                "  <response>\n" +
                "    <href>/</href>\n" +
                "    <propstat>\n" +
                "      <prop>\n")
        if (cTag != null)
                body.append("<CALDAV:getctag>$cTag</CALDAV:getctag>\n")
        body.append(
                "      </prop>\n" +
                "    </propstat>\n" +
                "  </response>\n" +
                "</multistatus>")
        val response = MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/xml")
                .setBody(body.toString())
        return response
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

}