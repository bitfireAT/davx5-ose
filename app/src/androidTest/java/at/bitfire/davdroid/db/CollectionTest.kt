/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

import android.security.NetworkSecurityPolicy
import androidx.test.filters.SmallTest
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.property.ResourceType
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class CollectionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsManager: SettingsManager

    @Before
    fun inject() {
        hiltRule.inject()
    }


    private lateinit var httpClient: HttpClient
    private val server = MockWebServer()

    @Before
    fun setUp() {
        httpClient = HttpClient.Builder().build()
        Assume.assumeTrue(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted)
    }

    @After
    fun shutDown() {
        httpClient.close()
    }


    @Test
    @SmallTest
    fun testFromDavResponseAddressBook() {
        // r/w address book
        server.enqueue(MockResponse()
                .setResponseCode(207)
                .setBody("<multistatus xmlns='DAV:' xmlns:CARD='urn:ietf:params:xml:ns:carddav'>" +
                        "<response>" +
                        "   <href>/</href>" +
                        "   <propstat><prop>" +
                        "       <resourcetype><collection/><CARD:addressbook/></resourcetype>" +
                        "       <displayname>My Contacts</displayname>" +
                        "       <CARD:addressbook-description>My Contacts Description</CARD:addressbook-description>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient.okHttpClient, server.url("/"))
                .propfind(0, ResourceType.NAME) { response, _ ->
            info = Collection.fromDavResponse(response) ?: throw IllegalArgumentException()
        }
        assertEquals(Collection.TYPE_ADDRESSBOOK, info.type)
        assertTrue(info.privWriteContent)
        assertTrue(info.privUnbind)
        assertNull(info.supportsVEVENT)
        assertNull(info.supportsVTODO)
        assertNull(info.supportsVJOURNAL)
        assertEquals("My Contacts", info.displayName)
        assertEquals("My Contacts Description", info.description)
    }

    @Test
    @SmallTest
    fun testFromDavResponseCalendar() {
        // read-only calendar, no display name
        server.enqueue(MockResponse()
                .setResponseCode(207)
                .setBody("<multistatus xmlns='DAV:' xmlns:CAL='urn:ietf:params:xml:ns:caldav' xmlns:ICAL='http://apple.com/ns/ical/'>" +
                        "<response>" +
                        "   <href>/</href>" +
                        "   <propstat><prop>" +
                        "       <resourcetype><collection/><CAL:calendar/></resourcetype>" +
                        "       <current-user-privilege-set><privilege><read/></privilege></current-user-privilege-set>" +
                        "       <CAL:calendar-description>My Calendar</CAL:calendar-description>" +
                        "       <CAL:calendar-timezone>tzdata</CAL:calendar-timezone>" +
                        "       <ICAL:calendar-color>#ff0000</ICAL:calendar-color>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient.okHttpClient, server.url("/"))
                .propfind(0, ResourceType.NAME) { response, _ ->
                    info = Collection.fromDavResponse(response) ?: throw IllegalArgumentException()
                }
        assertEquals(Collection.TYPE_CALENDAR, info.type)
        assertFalse(info.privWriteContent)
        assertFalse(info.privUnbind)
        assertNull(info.displayName)
        assertEquals("My Calendar", info.description)
        assertEquals(0xFFFF0000.toInt(), info.color)
        assertEquals("tzdata", info.timezone)
        assertTrue(info.supportsVEVENT!!)
        assertTrue(info.supportsVTODO!!)
        assertTrue(info.supportsVJOURNAL!!)
    }

    @Test
    @SmallTest
    fun testFromDavResponseWebcal() {
        // Webcal subscription
        server.enqueue(MockResponse()
                .setResponseCode(207)
                .setBody("<multistatus xmlns='DAV:' xmlns:CS='http://calendarserver.org/ns/'>" +
                        "<response>" +
                        "   <href>/webcal1</href>" +
                        "   <propstat><prop>" +
                        "       <displayname>Sample Subscription</displayname>" +
                        "       <resourcetype><collection/><CS:subscribed/></resourcetype>" +
                        "       <CS:source><href>webcals://example.com/1.ics</href></CS:source>" +
                        "   </prop></propstat>" +
                        "</response>" +
                        "</multistatus>"))

        lateinit var info: Collection
        DavResource(httpClient.okHttpClient, server.url("/"))
                .propfind(0, ResourceType.NAME) { response, _ ->
                    info = Collection.fromDavResponse(response) ?: throw IllegalArgumentException()
                }
        assertEquals(Collection.TYPE_WEBCAL, info.type)
        assertEquals("Sample Subscription", info.displayName)
        assertEquals("https://example.com/1.ics".toHttpUrl(), info.source)
    }

}
