/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalJtxCollection
import at.bitfire.davdroid.resource.LocalJtxCollectionStore
import at.bitfire.davdroid.resource.LocalJtxICalObject
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.ical4android.util.MiscUtils.toValues
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.StringReader
import javax.inject.Inject

/**
 * Ensure you have jtxBoard installed on the emulator, before running these tests, or you will see
 * an error on granting permissions.
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

    private val account = TestAccount.create()

    private lateinit var provider: ContentProviderClient
    private lateinit var syncManager: JtxSyncManager
    private lateinit var localJtxCollection: LocalJtxCollection

    @Before
    fun setUp() {
        hiltRule.inject()

        // For some reason we can't acquire the provider in the @BeforeClass method (before hilt
        // injection) so we do it here.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        provider = context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)!!

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
        localJtxCollectionStore.delete(localJtxCollection)
        serviceRepository.deleteAll()
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
        val reader = StringReader(calendar)

        // Should create "demo-calendar" without NPE on missing DTSTART
        syncManager.processICalObject("demo-calendar", "abc123", reader)

        // Note: We don't support starting a recurrence from DUE (RFC 5545  leaves it open to interpretation)
        // Specifically LocalJtxCollection.findRecurring() expects DTSTART value and can not use DUE

        // Verify main VTODO is created
        val vtodoObj = localJtxCollection.findByName("demo-calendar")!!
        assertEquals("Test Task (Main VTODO)", vtodoObj.summary)
        assertEquals("FREQ=WEEKLY;UNTIL=20250505T235959Z;INTERVAL=1;BYDAY=FR", vtodoObj.rrule)

        // Verify the RRULE exception VTODO was created
        provider.query(
            JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(account),
            null,
            "${JtxContract.JtxICalObject.UID} = ? AND ${JtxContract.JtxICalObject.SUMMARY} = ?",
            arrayOf(vtodoObj.uid, "Test Task (Exception)"),
            null
        )?.use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            val values = cursor.toValues()
            val localJtxIcalObject = LocalJtxICalObject.Factory.fromProvider(localJtxCollection, values)
            assertEquals("America/New_York", localJtxIcalObject.recuridTimezone)
            assertEquals("20250228T130000", localJtxIcalObject.recurid)
        }
    }


    companion object {

        @JvmField
        @ClassRule
        val permissionRule = GrantPermissionRule.grant(
            "at.techbee.jtx.permission.READ",
            "at.techbee.jtx.permission.WRITE"
        )!!

    }

}
