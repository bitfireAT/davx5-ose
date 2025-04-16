/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import android.content.Context
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.CaptureExceptionsRule
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxCollectionStore
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.techbee.jtx.JtxContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.StringReader
import javax.inject.Inject


/**
 * Ensure you have jtxBoard installed on the emulator, before running these tests. Otherwise they
 * will be skipped.
 */
@HiltAndroidTest
class JtxSyncManagerTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var httpClientBuilder: HttpClient.Builder

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var localJtxCollectionStore: LocalJtxCollectionStore

    @Inject
    lateinit var jtxSyncManagerFactory: JtxSyncManager.Factory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val permissionRule = CaptureExceptionsRule(
        GrantPermissionRule.grant(*TaskProvider.PERMISSIONS_JTX),
        SecurityException::class
    )

    private val account = TestAccount.create()

    private lateinit var provider: ContentProviderClient
    private lateinit var syncManager: JtxSyncManager
    private lateinit var localJtxCollection: LocalJtxCollection

    @Before
    fun setUp() {
        hiltRule.inject()

        // Check jtxBoard permissions were granted (+jtxBoard is installed); skip test otherwise
        assumeTrue(PermissionUtils.havePermissions(context, TaskProvider.PERMISSIONS_JTX))

        // Acquire the jtx content provider
        provider = context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!

        // Create dummy dependencies
        val service = Service(0, account.name, Service.TYPE_CALDAV, null)
        val serviceId = serviceRepository.insertOrReplace(service)
        val dbCollection = Collection(0, serviceId, type = Collection.TYPE_CALENDAR, url = "https://example.com".toHttpUrl())
        localJtxCollection = localJtxCollectionStore.create(provider, dbCollection)!!
        syncManager = jtxSyncManagerFactory.jtxSyncManager(
            account = account,
            extras = arrayOf(),
            httpClient = httpClientBuilder.build(),
            authority = JtxContract.AUTHORITY,
            syncResult = SyncResult(),
            localCollection = localJtxCollection,
            collection = dbCollection
        )
    }

    @After
    fun tearDown() {
        if (this::localJtxCollection.isInitialized)
            localJtxCollectionStore.delete(localJtxCollection)
        serviceRepository.deleteAll()
        if (this::provider.isInitialized)
        provider.closeCompat()
        TestAccount.remove(account)
    }


    @Test
    fun testProcessICalObject_addsVtodo() {
        val calendar = "BEGIN:VCALENDAR\n" +
            "PRODID:-Vivaldi Calendar V1.0//EN\n" +
            "VERSION:2.0\n" +
            "BEGIN:VTODO\n" +
            "SUMMARY:Test Task (Main VTODO)\n" +
            "DTSTAMP;VALUE=DATE-TIME:20250228T032800Z\n" +
            "UID:47a23c66-8c1a-4b44-bbe8-ebf33f8cf80f\n" +
            "END:VTODO\n" +
            "END:VCALENDAR"

        // Should create "demo-calendar"
        syncManager.processICalObject("demo-calendar", "abc123", StringReader(calendar))

        // Verify main VTODO is created
        val localJtxIcalObject = localJtxCollection.findByName("demo-calendar")!!
        assertEquals("47a23c66-8c1a-4b44-bbe8-ebf33f8cf80f", localJtxIcalObject.uid)
        assertEquals("abc123", localJtxIcalObject.eTag)
        assertEquals("Test Task (Main VTODO)", localJtxIcalObject.summary)
    }

    @Test
    fun testProcessICalObject_addsRecurringVtodo_withoutDtStart() {
        // Valid calendar example (See bitfireAT/davx5-ose#1265)
        // Note: We don't support starting a recurrence from DUE (RFC 5545  leaves it open to interpretation)
        val calendar = "BEGIN:VCALENDAR\n" +
            "PRODID:-Vivaldi Calendar V1.0//EN\n" +
            "VERSION:2.0\n" +
            "BEGIN:VTODO\n" +

            "SUMMARY:Test Task (Exception)\n" +
            "DTSTAMP;VALUE=DATE-TIME:20250228T032800Z\n" +
            "DUE;TZID=America/New_York:20250228T130000\n" +
            "RECURRENCE-ID;TZID=America/New_York:20250228T130000\n" +
            "UID:47a23c66-8c1a-4b44-bbe8-ebf33f8cf80f\n" +

            "END:VTODO\n" +
            "BEGIN:VTODO\n" +

            "SUMMARY:Test Task (Main VTODO)\n" +
            "DTSTAMP;VALUE=DATE-TIME:20250228T032800Z\n" +
            "DUE;TZID=America/New_York:20250228T130000\n" + // Due date will NOT be assumed as start for recurrence
            "SEQUENCE:1\n" +
            "UID:47a23c66-8c1a-4b44-bbe8-ebf33f8cf80f\n" +
            "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=FR;UNTIL=20250505T235959Z\n" +

            "END:VTODO\n" +
            "END:VCALENDAR"

        // Create and store calendar
        syncManager.processICalObject("demo-calendar", "abc123", StringReader(calendar))

        // Verify main VTODO was created with RRULE present
        val mainVtodo = localJtxCollection.findByName("demo-calendar")!!
        assertEquals("Test Task (Main VTODO)", mainVtodo.summary)
        assertEquals("FREQ=WEEKLY;UNTIL=20250505T235959Z;INTERVAL=1;BYDAY=FR", mainVtodo.rrule)

        // Verify the RRULE exception instance was created with correct recurrence-id timezone
        val vtodoException = localJtxCollection.findRecurInstance(
            uid = "47a23c66-8c1a-4b44-bbe8-ebf33f8cf80f",
            recurid = "20250228T130000"
        )!!
        assertEquals("Test Task (Exception)", vtodoException.summary)
        assertEquals("America/New_York", vtodoException.recuridTimezone)
    }

}