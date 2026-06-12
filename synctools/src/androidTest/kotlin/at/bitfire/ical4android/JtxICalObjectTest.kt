/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import android.database.DatabaseUtils
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.storage.jtx.JtxCollection
import at.bitfire.synctools.storage.jtx.JtxCollectionProvider
import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.bitfire.synctools.testProdId
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject
import at.techbee.jtx.JtxContract.JtxICalObject.Component
import at.techbee.jtx.JtxContract.asSyncAdapter
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import kotlin.jvm.optionals.getOrNull

class JtxICalObjectTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(TaskProvider.PERMISSIONS_JTX.toSet())

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var client: ContentProviderClient

    private val testAccount = Account(javaClass.name, JtxContract.JtxCollection.TEST_ACCOUNT_TYPE)
    private var collection: JtxCollection? = null
    private var sample: at.bitfire.ical4android.JtxICalObject? = null

    private val url = "https://jtx.techbee.at"
    private val displayname = "jtxTest"
    private val syncversion = JtxContract.VERSION

    private val cvCollection = ContentValues().apply {
        put(JtxContract.JtxCollection.ACCOUNT_TYPE, testAccount.type)
        put(JtxContract.JtxCollection.ACCOUNT_NAME, testAccount.name)
        put(JtxContract.JtxCollection.URL, url)
        put(JtxContract.JtxCollection.DISPLAYNAME, displayname)
        put(JtxContract.JtxCollection.SYNC_VERSION, syncversion)
    }

    @Before
    fun setUp() {
        val clientOrNull = context.contentResolver.acquireContentProviderClient(JtxContract.AUTHORITY)
        Assume.assumeNotNull(clientOrNull)
        client = clientOrNull!!

        collection = JtxCollectionProvider(testAccount, client).createAndGetCollection(cvCollection)

        sample = JtxICalObject(collection!!).apply {
            this.summary = "summ"
            this.description = "desc"
            this.dtstart = System.currentTimeMillis()
            this.dtstartTimezone = "Europe/Vienna"
            this.dtend = System.currentTimeMillis()
            this.dtendTimezone = "Europe/Paris"
            this.status = JtxICalObject.StatusJournal.FINAL.name
            this.xstatus = "my status"
            this.classification = JtxICalObject.Classification.PUBLIC.name
            this.url = "https://jtx.techbee.at"
            this.contact = "jtx@techbee.at"
            this.geoLat = 48.2082
            this.geoLong = 16.3738
            this.location = "Vienna"
            this.locationAltrep = "Wien"
            this.geofenceRadius = 10
            this.percent = 99
            this.priority = 1
            this.due = System.currentTimeMillis()
            this.dueTimezone = "Europe/Berlin"
            this.completed = System.currentTimeMillis()
            this.completedTimezone = "Europe/Budapest"
            this.duration = "P15DT5H0M20S"
            this.rrule = "FREQ=YEARLY;INTERVAL=2;BYMONTH=1;BYDAY=SU;BYHOUR=8,9;BYMINUTE=30"
            this.exdate = System.currentTimeMillis().toString()
            this.rdate = System.currentTimeMillis().toString()
            this.recurid = "1635796608864-b228364a-e633-449a-aeb2-d1a96941377c@at.techbee.jtx"
            this.uid = "1635796608864-b228364a-e633-449a-aeb2-d1a96941377c@at.techbee.jtx"
            this.created = System.currentTimeMillis()
            this.lastModified = System.currentTimeMillis()
            this.dtstamp = System.currentTimeMillis()
            this.sequence = 1
            this.color = -2298423
            this.dirty = true
            this.deleted = false
            this.fileName = "test.ics"
            this.eTag = "0"
            this.scheduleTag = "0"
            this.flags = 0
        }

    }

    @After
    fun tearDown() {
        collection?.delete()
        val collections = JtxCollectionProvider(testAccount, client).findCollections()
        assertEquals(0, collections.size)
        client.close()
    }


    @Test fun check_SUMMARY() = insertRetrieveAssertString(JtxICalObject.SUMMARY, sample?.summary, Component.VJOURNAL.name)
    @Test fun check_DESCRIPTION() = insertRetrieveAssertString(JtxICalObject.DESCRIPTION, sample?.description, Component.VJOURNAL.name)
    @Test fun check_DTSTART() = insertRetrieveAssertLong(JtxICalObject.DTSTART, sample?.dtstart, Component.VJOURNAL.name)
    @Test fun check_DTSTART_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.DTSTART_TIMEZONE, sample?.dtstartTimezone, Component.VJOURNAL.name)
    @Test fun check_DTEND() = insertRetrieveAssertLong(JtxICalObject.DTEND, sample?.dtend, Component.VJOURNAL.name)
    @Test fun check_DTEND_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.DTEND_TIMEZONE, sample?.dtendTimezone, Component.VJOURNAL.name)
    @Test fun check_STATUS() = insertRetrieveAssertString(JtxICalObject.STATUS, sample?.status, Component.VJOURNAL.name)
    @Test fun check_XSTATUS() {
        val jtxVersionCode = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo("at.techbee.jtx", 0))
        Assume.assumeTrue(jtxVersionCode > 204020003)
        insertRetrieveAssertString(JtxICalObject.EXTENDED_STATUS, sample?.xstatus, Component.VJOURNAL.name)
    }

    @Test fun check_CLASSIFICATION() = insertRetrieveAssertString(JtxICalObject.CLASSIFICATION, sample?.classification, Component.VJOURNAL.name)
    @Test fun check_URL() = insertRetrieveAssertString(JtxICalObject.URL, sample?.url, Component.VJOURNAL.name)
    @Test fun check_CONTACT() = insertRetrieveAssertString(JtxICalObject.CONTACT, sample?.contact, Component.VJOURNAL.name)
    @Test fun check_GEO_LAT() = insertRetrieveAssertDouble(JtxICalObject.GEO_LAT, sample?.geoLat, Component.VJOURNAL.name)
    @Test fun check_GEO_LONG() = insertRetrieveAssertDouble(JtxICalObject.GEO_LONG, sample?.geoLong, Component.VJOURNAL.name)
    @Test fun check_LOCATION() = insertRetrieveAssertString(JtxICalObject.LOCATION, sample?.location, Component.VJOURNAL.name)
    @Test fun check_LOCATION_ALTREP() = insertRetrieveAssertString(JtxICalObject.LOCATION_ALTREP, sample?.locationAltrep, Component.VJOURNAL.name)
    @Test fun check_GEOFENCE_RADIUS() {
        val jtxVersionCode = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo("at.techbee.jtx", 0))
        Assume.assumeTrue(jtxVersionCode > 204020003)
        insertRetrieveAssertInt(JtxICalObject.GEOFENCE_RADIUS, sample?.geofenceRadius, Component.VJOURNAL.name)
    }

    @Test fun check_PERCENT() = insertRetrieveAssertInt(JtxICalObject.PERCENT, sample?.percent, Component.VJOURNAL.name)
    @Test fun check_PRIORITY() = insertRetrieveAssertInt(JtxICalObject.PRIORITY, sample?.priority, Component.VJOURNAL.name)
    @Test fun check_DUE() = insertRetrieveAssertLong(JtxICalObject.DUE, sample?.due, Component.VJOURNAL.name)
    @Test fun check_DUE_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.DUE_TIMEZONE, sample?.dueTimezone, Component.VJOURNAL.name)
    @Test fun check_COMPLETED() = insertRetrieveAssertLong(JtxICalObject.COMPLETED, sample?.completed, Component.VJOURNAL.name)
    @Test fun check_COMPLETED_TIMEZONE() = insertRetrieveAssertString(JtxICalObject.COMPLETED_TIMEZONE, sample?.completedTimezone, Component.VJOURNAL.name)
    @Test fun check_DURATION() = insertRetrieveAssertString(JtxICalObject.DURATION, sample?.duration, Component.VJOURNAL.name)
    @Test fun check_RRULE() = insertRetrieveAssertString(JtxICalObject.RRULE, sample?.rrule, Component.VJOURNAL.name)
    @Test fun check_RDATE() = insertRetrieveAssertString(JtxICalObject.RDATE, sample?.rdate, Component.VJOURNAL.name)
    @Test fun check_EXDATE() = insertRetrieveAssertString(JtxICalObject.EXDATE, sample?.exdate, Component.VJOURNAL.name)
    @Test fun check_RECURID() = insertRetrieveAssertString(JtxICalObject.RECURID, sample?.recurid, Component.VJOURNAL.name)
    @Test fun check_UID() = insertRetrieveAssertString(JtxICalObject.UID, sample?.uid, Component.VJOURNAL.name)
    @Test fun check_CREATED() = insertRetrieveAssertLong(JtxICalObject.CREATED, sample?.created, Component.VJOURNAL.name)
    @Test fun check_DTSTAMP() = insertRetrieveAssertLong(JtxICalObject.DTSTAMP, sample?.dtstamp, Component.VJOURNAL.name)
    @Test fun check_LAST_MODIFIED() = insertRetrieveAssertLong(JtxICalObject.LAST_MODIFIED, sample?.lastModified, Component.VJOURNAL.name)
    @Test fun check_SEQUENCE() = insertRetrieveAssertLong(JtxICalObject.SEQUENCE, sample?.sequence, Component.VJOURNAL.name)
    @Test fun check_COLOR() = insertRetrieveAssertInt(JtxICalObject.COLOR, sample?.color, Component.VJOURNAL.name)
    @Test fun check_DIRTY() = insertRetrieveAssertBoolean(JtxICalObject.DIRTY, sample?.dirty, Component.VJOURNAL.name)
    @Test fun check_DELETED() = insertRetrieveAssertBoolean(JtxICalObject.DELETED,sample?.deleted, Component.VJOURNAL.name)
    @Test fun check_FILENAME() = insertRetrieveAssertString(JtxICalObject.FILENAME, sample?.fileName, Component.VJOURNAL.name)
    @Test fun check_ETAG() = insertRetrieveAssertString(JtxICalObject.ETAG, sample?.eTag, Component.VJOURNAL.name)
    @Test fun check_SCHEDULETAG() = insertRetrieveAssertString(JtxICalObject.SCHEDULETAG, sample?.scheduleTag, Component.VJOURNAL.name)
    @Test fun check_FLAGS() = insertRetrieveAssertInt(JtxICalObject.FLAGS, sample?.flags, Component.VJOURNAL.name)

    private fun insertRetrieveAssertString(field: String, fieldContent: String?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsString(field))
        }
    }


    private fun insertRetrieveAssertBoolean(field: String, fieldContent: Boolean?, component: String) {
        // ATTENTION: getAsBoolean() should not be used as it would interpret "0" and "1" both as false for API-levels < 26
        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            val retrievedFieldContent = itemCV.getAsString(field)
            val retrievedFieldBoolean = retrievedFieldContent == "1" || retrievedFieldContent == "true"
            assertEquals(fieldContent, retrievedFieldBoolean)
        }
    }

    private fun insertRetrieveAssertLong(field: String, fieldContent: Long?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsLong(field))
        }
    }

    private fun insertRetrieveAssertInt(field: String, fieldContent: Int?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsInteger(field))
        }
    }

    private fun insertRetrieveAssertDouble(field: String, fieldContent: Double?, component: String) {

        assertNotNull(fieldContent)    // fieldContent should not be null, check if the testcase was built correctly

        val cv = ContentValues().apply {
            put(field, fieldContent)
            put(JtxICalObject.COMPONENT, component)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        client.query(uri, null, null, null, null)?.use {
            val itemCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, itemCV)
            assertEquals(fieldContent, itemCV.getAsDouble(field))
        }
    }

    @Test
    fun assertComment() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val comment = at.bitfire.ical4android.JtxICalObject.Comment(
            text = "comment",
            altrep = "Kommentar",
            language = "de",
            other = "X-OTHER:Test"
        )

        val commentCV = ContentValues().apply {
            put(JtxContract.JtxComment.TEXT, comment.text)
            put(JtxContract.JtxComment.ALTREP, comment.altrep)
            put(JtxContract.JtxComment.LANGUAGE, comment.language)
            put(JtxContract.JtxComment.OTHER, comment.other)
            put(JtxContract.JtxComment.ICALOBJECT_ID, id)
        }

        val commentUri = client.insert(JtxContract.JtxComment.CONTENT_URI.asSyncAdapter(testAccount), commentCV)!!
        client.query(commentUri, null, null, null, null)?.use {
            val retrievedCommentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedCommentCV)
            assertEquals(comment.text, retrievedCommentCV.getAsString(JtxContract.JtxComment.TEXT))
            assertEquals(comment.altrep, retrievedCommentCV.getAsString(JtxContract.JtxComment.ALTREP))
            assertEquals(comment.language, retrievedCommentCV.getAsString(JtxContract.JtxComment.LANGUAGE))
            assertEquals(comment.other, retrievedCommentCV.getAsString(JtxContract.JtxComment.OTHER))
        }
    }

    @Test
    fun assertResource() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val resource = at.bitfire.ical4android.JtxICalObject.Resource(
            text = "projector",
            altrep = "Projektor",
            language = "de",
            other = "X-OTHER:Test"
        )

        val resourceCV = ContentValues().apply {
            put(JtxContract.JtxResource.TEXT, resource.text)
            put(JtxContract.JtxResource.LANGUAGE, resource.language)
            put(JtxContract.JtxResource.OTHER, resource.other)
            put(JtxContract.JtxResource.ICALOBJECT_ID, id)
        }

        val resourceUri = client.insert(JtxContract.JtxResource.CONTENT_URI.asSyncAdapter(testAccount), resourceCV)!!
        client.query(resourceUri, null, null, null, null)?.use {
            val retrievedResourceCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedResourceCV)
            assertEquals(resource.text, retrievedResourceCV.getAsString(JtxContract.JtxResource.TEXT))
            assertEquals(resource.language, retrievedResourceCV.getAsString(JtxContract.JtxResource.LANGUAGE))
            assertEquals(resource.other, retrievedResourceCV.getAsString(JtxContract.JtxResource.OTHER))
        }
    }

    @Test
    fun assertAttendee() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attendee = at.bitfire.ical4android.JtxICalObject.Attendee(
            caladdress = "jtx@techbee.at",
            cutype = JtxContract.JtxAttendee.Cutype.INDIVIDUAL.name,
            member = "group",
            partstat = "0",
            role = JtxContract.JtxAttendee.Role.`REQ-PARTICIPANT`.name,
            rsvp = false,
            delegatedfrom = "jtx@techbee.at",
            delegatedto = "jtx@techbee.at",
            sentby = "jtx@techbee.at",
            cn = "jtx Board",
            dir = "dir",
            language = "de",
            other = "X-OTHER:Test"
        )

        val attendeeCV = ContentValues().apply {
            put(JtxContract.JtxAttendee.CALADDRESS, attendee.caladdress)
            put(JtxContract.JtxAttendee.CUTYPE, attendee.cutype)
            put(JtxContract.JtxAttendee.MEMBER, attendee.member)
            put(JtxContract.JtxAttendee.PARTSTAT, attendee.partstat)
            put(JtxContract.JtxAttendee.ROLE, attendee.role)
            put(JtxContract.JtxAttendee.RSVP, attendee.rsvp)
            put(JtxContract.JtxAttendee.DELEGATEDFROM, attendee.delegatedfrom)
            put(JtxContract.JtxAttendee.DELEGATEDTO, attendee.delegatedto)
            put(JtxContract.JtxAttendee.SENTBY, attendee.sentby)
            put(JtxContract.JtxAttendee.CN, attendee.cn)
            put(JtxContract.JtxAttendee.DIR, attendee.dir)
            put(JtxContract.JtxAttendee.LANGUAGE, attendee.language)
            put(JtxContract.JtxAttendee.OTHER, attendee.other)
            put(JtxContract.JtxAttendee.ICALOBJECT_ID, id)
        }

        val attendeeUri = client.insert(JtxContract.JtxAttendee.CONTENT_URI.asSyncAdapter(testAccount), attendeeCV)!!
        client.query(attendeeUri, null, null, null, null)?.use {
            val retrievedAttendeeCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttendeeCV)
            assertEquals(attendee.caladdress, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.CALADDRESS))
            assertEquals(attendee.cutype, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.CUTYPE))
            assertEquals(attendee.member, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.MEMBER))
            assertEquals(attendee.partstat, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.PARTSTAT))
            assertEquals(attendee.role, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.ROLE))
            assertEquals(attendee.rsvp, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.RSVP) == "1"
                    || retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.RSVP) == "true")
            assertEquals(attendee.delegatedfrom, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.DELEGATEDFROM))
            assertEquals(attendee.delegatedto, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.DELEGATEDTO))
            assertEquals(attendee.sentby, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.SENTBY))
            assertEquals(attendee.cn, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.CN))
            assertEquals(attendee.dir, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.DIR))
            assertEquals(attendee.language, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.LANGUAGE))
            assertEquals(attendee.other, retrievedAttendeeCV.getAsString(JtxContract.JtxAttendee.OTHER))
        }
    }

    @Test
    fun assertCategory() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val category = at.bitfire.ical4android.JtxICalObject.Category(
            text = "projector",
        )

        val categoryCV = ContentValues().apply {
            put(JtxContract.JtxCategory.TEXT, category.text)
            put(JtxContract.JtxCategory.ICALOBJECT_ID, id)
        }

        val categoryUri = client.insert(JtxContract.JtxCategory.CONTENT_URI.asSyncAdapter(testAccount), categoryCV)!!
        client.query(categoryUri, null, null, null, null)?.use {
            val retrievedCategoryCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedCategoryCV)
            assertEquals(category.text, retrievedCategoryCV.getAsString(JtxContract.JtxCategory.TEXT))
        }
    }

    @Test
    fun assertAttachment_without_binary() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attachment = at.bitfire.ical4android.JtxICalObject.Attachment(
            uri = "https://jtx.techbee.at/sample.pdf",
            fmttype = "application/pdf",
            other = "X-OTHER:other",
        )

        val attachmentCV = ContentValues().apply {
            put(JtxContract.JtxAttachment.URI, attachment.uri)
            put(JtxContract.JtxAttachment.FMTTYPE, attachment.fmttype)
            put(JtxContract.JtxAttachment.OTHER, attachment.other)
            put(JtxContract.JtxAttachment.ICALOBJECT_ID, id)
        }

        val attachmentUri = client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(testAccount), attachmentCV)!!
        client.query(attachmentUri, null, null, null, null)?.use {
            val retrievedAttachmentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttachmentCV)
            assertEquals(attachment.uri, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.URI))
            assertEquals(attachment.fmttype, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.FMTTYPE))
            assertEquals(attachment.other, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.OTHER))
        }
    }


    @Test
    fun assertAttachment_without_binary_and_uri() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attachment = at.bitfire.ical4android.JtxICalObject.Attachment(
            fmttype = "application/pdf"
        )

        val attachmentCV = ContentValues().apply {
            put(JtxContract.JtxAttachment.FMTTYPE, attachment.fmttype)
            put(JtxContract.JtxAttachment.ICALOBJECT_ID, id)
        }

        val attachmentUri = client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(testAccount), attachmentCV)!!
        client.query(attachmentUri, null, null, null, null)?.use {
            val retrievedAttachmentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttachmentCV)
            assertEquals(attachment.fmttype, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.FMTTYPE))
            assertNotNull(retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.URI))
        }

        val textIn = "jtx Board rulz"
        val pfd = client.openFile(attachmentUri, "w", null)
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).write(textIn.toByteArray())

        val pfd2 = client.openFile(attachmentUri, "r", null)
        val textCompare = String(ParcelFileDescriptor.AutoCloseInputStream(pfd2).readBytes())

        Assert.assertEquals(textIn, textCompare)

    }

    @Test
    fun assertAttachment_with_binary() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val attachment = at.bitfire.ical4android.JtxICalObject.Attachment(
            //uri = "https://jtx.techbee.at/sample.pdf",
            binary = "anR4IEJvYXJk",
            fmttype = "application/pdf",
            other = "X-OTHER:other",
        )

        val attachmentCV = ContentValues().apply {
            //put(JtxContract.JtxAttachment.URI, attachment.uri)
            put(JtxContract.JtxAttachment.BINARY, attachment.binary)
            put(JtxContract.JtxAttachment.FMTTYPE, attachment.fmttype)
            put(JtxContract.JtxAttachment.OTHER, attachment.other)
            put(JtxContract.JtxAttachment.ICALOBJECT_ID, id)
        }

        val attachmentUri = client.insert(JtxContract.JtxAttachment.CONTENT_URI.asSyncAdapter(testAccount), attachmentCV)!!
        client.query(attachmentUri, null, null, null, null)?.use {
            val retrievedAttachmentCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAttachmentCV)
            assertTrue(retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.URI).startsWith("content://"))   // binary was replaced by content uri
            assertNull(retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.BINARY))
            assertEquals(attachment.fmttype, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.FMTTYPE))
            assertEquals(attachment.other, retrievedAttachmentCV.getAsString(JtxContract.JtxAttachment.OTHER))
        }
    }

    @Test
    fun assertRelatedto() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val relatedto = at.bitfire.ical4android.JtxICalObject.RelatedTo(
            text = "1635164243187-3fd0f89e-d017-471e-a046-71ff1844d58e@at.techbee.jtx",
            reltype = JtxContract.JtxRelatedto.Reltype.CHILD.name,
            other = "X-OTHER: other"
        )

        val relatedtoCV = ContentValues().apply {
            put(JtxContract.JtxRelatedto.TEXT, relatedto.text)
            put(JtxContract.JtxRelatedto.RELTYPE, relatedto.reltype)
            put(JtxContract.JtxRelatedto.OTHER, relatedto.other)
            put(JtxContract.JtxRelatedto.ICALOBJECT_ID, id)
        }

        val relatedtoUri = client.insert(JtxContract.JtxRelatedto.CONTENT_URI.asSyncAdapter(testAccount), relatedtoCV)!!
        client.query(relatedtoUri, null, null, null, null)?.use {
            val retrievedRelatedtoCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedRelatedtoCV)
            assertEquals(relatedto.text, retrievedRelatedtoCV.getAsString(JtxContract.JtxRelatedto.TEXT))
            assertEquals(relatedto.reltype, retrievedRelatedtoCV.getAsString(JtxContract.JtxRelatedto.RELTYPE))
            assertEquals(relatedto.other, retrievedRelatedtoCV.getAsString(JtxContract.JtxRelatedto.OTHER))
        }
    }

    @Test
    fun assertAlarm_basic() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val alarm = at.bitfire.ical4android.JtxICalObject.Alarm(
            action = JtxContract.JtxAlarm.AlarmAction.AUDIO.name,
            description = "desc",
            summary = "summary",
            duration = "PT15M",
            triggerTime = 1641557428506L,
            repeat = "4",
            attach = "ftp://example.com/pub/sounds/bell-01.aud",
            other = "X-OTHER: other",
            )

        val alarmCV = ContentValues().apply {
            put(JtxContract.JtxAlarm.ACTION, alarm.action)
            put(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
            put(JtxContract.JtxAlarm.SUMMARY, alarm.summary)
            put(JtxContract.JtxAlarm.DURATION, alarm.duration)
            put(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
            put(JtxContract.JtxAlarm.REPEAT, alarm.repeat)
            put(JtxContract.JtxAlarm.ATTACH, alarm.attach)
            put(JtxContract.JtxAlarm.OTHER, alarm.other)
            put(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
        }

        val alarmUri = client.insert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(testAccount), alarmCV)!!
        client.query(alarmUri, null, null, null, null)?.use {
            val retrievedAlarmCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAlarmCV)
            assertEquals(alarm.action, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ACTION))
            assertEquals(alarm.description, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
            assertEquals(alarm.summary, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.SUMMARY))
            assertEquals(alarm.duration, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.DURATION))
            assertEquals(alarm.repeat, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.REPEAT))
            assertEquals(alarm.attach, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ATTACH))
            assertEquals(alarm.other, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.OTHER))
        }
    }


    @Test
    fun assertAlarm_trigger_duration() {
        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VTODO.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val alarm = at.bitfire.ical4android.JtxICalObject.Alarm(
            action = JtxContract.JtxAlarm.AlarmAction.DISPLAY.name,
            description = "desc",
            triggerRelativeDuration = "-PT5M",
            triggerRelativeTo = JtxContract.JtxAlarm.AlarmRelativeTo.START.name
        )

        val alarmCV = ContentValues().apply {
            put(JtxContract.JtxAlarm.ACTION, alarm.action)
            put(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
            put(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION, alarm.triggerRelativeDuration)
            put(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO, alarm.triggerRelativeTo)
            put(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
        }

        val alarmUri = client.insert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(testAccount), alarmCV)!!
        client.query(alarmUri, null, null, null, null)?.use {
            val retrievedAlarmCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAlarmCV)
            assertEquals(alarm.action, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ACTION))
            assertEquals(alarm.description, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
            assertEquals(alarm.triggerRelativeTo, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_TO))
            assertEquals(alarm.triggerRelativeDuration, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.TRIGGER_RELATIVE_DURATION))
        }
    }

    @Test
    fun assertAlarm_trigger_time_withoutTZ() {
        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VTODO.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val alarm = at.bitfire.ical4android.JtxICalObject.Alarm(
            action = JtxContract.JtxAlarm.AlarmAction.DISPLAY.name,
            description = "desc",
            triggerTime = 1641557428506L
        )

        val alarmCV = ContentValues().apply {
            put(JtxContract.JtxAlarm.ACTION, alarm.action)
            put(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
            put(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
            put(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
        }

        val alarmUri = client.insert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(testAccount), alarmCV)!!
        client.query(alarmUri, null, null, null, null)?.use {
            val retrievedAlarmCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAlarmCV)
            assertEquals(alarm.action, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ACTION))
            assertEquals(alarm.description, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
            assertEquals(alarm.triggerTime, retrievedAlarmCV.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME))
        }
    }

    @Test
    fun assertAlarm_trigger_time_UTC() {
        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VTODO.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val alarm = at.bitfire.ical4android.JtxICalObject.Alarm(
            action = JtxContract.JtxAlarm.AlarmAction.DISPLAY.name,
            description = "desc",
            triggerTime = 1641557428506L,
            triggerTimezone = "UTC"
        )

        val alarmCV = ContentValues().apply {
            put(JtxContract.JtxAlarm.ACTION, alarm.action)
            put(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
            put(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
            put(JtxContract.JtxAlarm.TRIGGER_TIMEZONE, alarm.triggerTimezone)
            put(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
        }

        val alarmUri = client.insert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(testAccount), alarmCV)!!
        client.query(alarmUri, null, null, null, null)?.use {
            val retrievedAlarmCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAlarmCV)
            assertEquals(alarm.action, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ACTION))
            assertEquals(alarm.description, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
            assertEquals(alarm.triggerTime, retrievedAlarmCV.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME))
            assertEquals(alarm.triggerTimezone, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE))
        }
    }


    @Test
    fun assertAlarm_trigger_time_Vienna() {
        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VTODO.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val alarm = at.bitfire.ical4android.JtxICalObject.Alarm(
            action = "DISPLAY",
            description = "desc",
            triggerTime = 1641557428506L,
            triggerTimezone = "Europe/Vienna"
        )

        val alarmCV = ContentValues().apply {
            put(JtxContract.JtxAlarm.ACTION, alarm.action)
            put(JtxContract.JtxAlarm.DESCRIPTION, alarm.description)
            put(JtxContract.JtxAlarm.TRIGGER_TIME, alarm.triggerTime)
            put(JtxContract.JtxAlarm.TRIGGER_TIMEZONE, alarm.triggerTimezone)
            put(JtxContract.JtxAlarm.ICALOBJECT_ID, id)
        }

        val alarmUri = client.insert(JtxContract.JtxAlarm.CONTENT_URI.asSyncAdapter(testAccount), alarmCV)!!
        client.query(alarmUri, null, null, null, null)?.use {
            val retrievedAlarmCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedAlarmCV)
            assertEquals(alarm.action, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.ACTION))
            assertEquals(alarm.description, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.DESCRIPTION))
            assertEquals(alarm.triggerTime, retrievedAlarmCV.getAsLong(JtxContract.JtxAlarm.TRIGGER_TIME))
            assertEquals(alarm.triggerTimezone, retrievedAlarmCV.getAsString(JtxContract.JtxAlarm.TRIGGER_TIMEZONE))
        }
    }


    @Test
    fun assertUnknown() {

        val cv = ContentValues().apply {
            put(JtxICalObject.COMPONENT, Component.VJOURNAL.name)
            put(JtxICalObject.ICALOBJECT_COLLECTIONID, collection?.id)
        }
        val uri = client.insert(JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)!!
        val id = uri.lastPathSegment

        val unknown = at.bitfire.ical4android.JtxICalObject.Unknown(
            value = "X-PROP:my value"
        )

        val unknownCV = ContentValues().apply {
            put(JtxContract.JtxUnknown.UNKNOWN_VALUE, unknown.value)
            put(JtxContract.JtxUnknown.ICALOBJECT_ID, id)
        }

        val unknownUri = client.insert(JtxContract.JtxUnknown.CONTENT_URI.asSyncAdapter(testAccount), unknownCV)!!
        client.query(unknownUri, null, null, null, null)?.use {
            val retrievedUnknownCV = ContentValues()
            it.moveToFirst()
            DatabaseUtils.cursorRowToContentValues(it, retrievedUnknownCV)
            assertEquals(unknown.value, retrievedUnknownCV.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE))
        }
    }




    /** TESTS TO READ A FILE; INSERT IT IN THE CONTENT PROVIDER; READ THE CONTENT PROVIDER AND THEN COMPARE IF THE CONTENT OF THE GENERATED ICAL IS STILL THE SAME AS FROM THE SERVER */

    // VTODO
    @Test fun check_input_equals_output_vtodo_most_fields1() = compare_properties("jtx/vtodo/most-fields1.ics", listOf("EXDATE", "RDATE"))
    @Test fun check_input_equals_output_vtodo_most_fields2() = compare_properties("jtx/vtodo/most-fields2.ics", null)
    @Test fun check_input_equals_output_vtodo_utf8() = compare_properties("jtx/vtodo/utf8.ics", null)
    @Test fun check_input_equals_output_vtodo_rfc5545_sample() = compare_properties("jtx/vtodo/rfc5545-sample1.ics", null)
    @Test fun check_input_equals_output_vtodo_empty_priority() = compare_properties("jtx/vtodo/empty-priority.ics", null)
    @Test fun check_input_equals_output_vtodo_latin1() = compare_properties("jtx/vtodo/latin1.ics", null)
    @Test fun check_input_equals_output_vtodo_attach() = compare_properties("jtx/vtodo/attach.ics", null)

    // VJOURNAL
    @Test fun check_input_equals_output_vjournal_default_example() = compare_properties("jtx/vjournal/default-example.ics", null)
    @Test fun check_input_equals_output_vjournal_default_example_note() = compare_properties("jtx/vjournal/default-example-note.ics", null)
    @Test fun check_input_equals_output_vjournal_utf8() = compare_properties("jtx/vjournal/utf8.ics", null)
    @Test fun check_input_equals_output_vjournal_two_line() = compare_properties("jtx/vjournal/two-line-description-without-crlf.ics", listOf("CREATED", "LAST-MODIFIED", "DTSTART"))   // expected:<CREATED;VALUE=DATE-TIME:20131008T205713Z > but was:<CREATED:20131008T205713Z  is ignored here; expected:<LAST-MODIFIED;VALUE=DATE-TIME:20131008T205740> but was:<LAST-MODIFIED:20131008T205740Z  is ignored here as the actual is the correct result
    //@Test fun check_input_equals_output_vjournal_two_journals() = compare_properties("jtx/vjournal/two-events-without-exceptions.ics", null) // this file contains two events, the direct comparison through the given method would not work
    @Test fun check_input_equals_output_vjournal_recurring() = compare_properties("jtx/vjournal/recurring.ics", null)
    //@Test fun check_input_equals_output_vjournal_outlook_theoretical() = compare_properties("jtx/vjournal/outlook-theoretical.ics", null)    // includes custom timezones, ignored for now
    @Test fun check_input_equals_output_vjournal_outlook_theoretical2() = compare_properties("jtx/vjournal/outlook-theoretical2.ics", null)
    @Test fun check_input_equals_output_vjournal_latin1() = compare_properties("jtx/vjournal/latin1.ics", null)
    @Test fun check_input_equals_output_vjournal_journalonthatday() = compare_properties("jtx/vjournal/journal-on-that-day.ics", null)
    //@Test fun check_input_equals_output_vjournal_dst_only_vtimezone() = compare_properties("jtx/vjournal/dst-only-vtimezone.ics", null)    // includes custom timezones, ignored for now
    @Test fun check_input_equals_output_vjournal_all_day() = compare_properties("jtx/vjournal/all-day.ics", null)


    @Test
    fun fromReader_should_set_recurid_values() {
        val ical = """
            BEGIN:VCALENDAR
            PRODID:irrelevant
            VERSION:2.0
            BEGIN:VTODO
            SUMMARY:Test Task (Exception)
            DTSTAMP:20250228T032800Z
            DUE;TZID=America/New_York:20250228T130000
            RECURRENCE-ID;TZID=America/New_York:20250228T130000
            UID:47a23c66-8c1a-4b44-bbe8-ebf33f8cf80f
            END:VTODO
            END:VCALENDAR
        """.trimIndent()
        val reader = StringReader(ical)

        val iCalObjects = at.bitfire.ical4android.JtxICalObject.fromReader(reader, collection!!)

        assertEquals(1, iCalObjects.size)
        assertEquals("20250228T130000", iCalObjects.first().recurid)
        assertEquals("America/New_York", iCalObjects.first().recuridTimezone)
    }

    @Test
    fun testFromReader_parses_inline_attachment() {
        val attachmentData = "test data".toByteArray()
        val base64Data = Base64.encodeToString(attachmentData, Base64.NO_WRAP)
        val icalString = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//Test//Test//EN\r\n" +
                "BEGIN:VTODO\r\n" +
                "UID:test-attachment\r\n" +
                "ATTACH;ENCODING=BASE64;VALUE=BINARY:$base64Data\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n"

        val objects = at.bitfire.ical4android.JtxICalObject.fromReader(StringReader(icalString), collection!!)

        assertEquals(1, objects.size)
        assertEquals(1, objects[0].attachments.size)

        val attachment = objects[0].attachments[0]
        assertNotNull(attachment.binary)
        val decoded = Base64.decode(attachment.binary, Base64.DEFAULT)
        assertArrayEquals(attachmentData, decoded)
    }


    @Test
    fun getICalendarFormat_recurringVToDo_exceptionsMustHaveUidAndRecurrenceId() {
        // Prepare data object with recurring VTODO + exception
        val uid = "recurring-vtodo-uid@test"
        val mainObject = JtxICalObject(collection!!).apply {
            component = Component.VTODO.name
            this.uid = uid
            summary = "Recurring task"
            dtstart = 1744970400000L  // 2025-04-18T12:00:00Z
            rrule = "FREQ=MONTHLY;UNTIL=20270417T110000Z"
        }

        val recurId = "20250518T120000Z"
        val exceptionObject = JtxICalObject(collection!!).apply {
            component = Component.VTODO.name
            this.uid = uid
            summary = "Recurring task (exception)"
            this.recurid = recurId
        }
        mainObject.recurInstances += exceptionObject

        // Generate iCalendar VTODO from data object
        val ical = mainObject.getICalendarFormat(testProdId)
        assertNotNull(ical)

        // Verify VTODOs
        val vtodos = ical!!.getComponents<VToDo>(Component.VTODO.name)
        assertEquals(2, vtodos.size)
        assertTrue("VTODOs must have same UID", vtodos.all { it.uid.get().value == uid })

        // Verify RECURRENCE-ID of exception
        val exceptions = vtodos.filter { it.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).isPresent }
        assertEquals(1, exceptions.size)
        val exception = exceptions.first()
        assertEquals(recurId, exception.recurrenceId?.value)
    }

    @Test
    fun getICalendarFormat_VToDo_AlarmHasProperties() {
        val obj = JtxICalObject(collection!!).apply {
            component = Component.VTODO.name
            uid = "vtodo-alarm-test@test"
            summary = "Task with alarm"
            alarms += at.bitfire.ical4android.JtxICalObject.Alarm(
                action = JtxContract.JtxAlarm.AlarmAction.DISPLAY.name,
                triggerRelativeDuration = "-PT15M"
            )
        }

        // Generate iCalendar VTODO from data object
        val ical = obj.getICalendarFormat(testProdId)
        assertNotNull(ical)

        // Extract VALARM
        val vtodos = ical!!.getComponents<VToDo>(Component.VTODO.name)
        assertEquals(1, vtodos.size)
        val valarms = vtodos[0].getComponents<VAlarm>(net.fortuna.ical4j.model.Component.VALARM)
        assertEquals(1, valarms.size)

        // Verify VALARM properties
        val valarm = valarms.first()
        assertEquals(ImmutableAction.DISPLAY, valarm.getProperty<Action>(Property.ACTION).get())
        assertEquals("-PT15M", valarm.getProperty<Trigger>(Property.TRIGGER).get().value)
    }

    @Test
    fun getICalendarFormat_ExDateAllDay_hasValueDateParameter() {
        // Test that ExDate for all-day events includes VALUE=DATE parameter
        val obj = JtxICalObject(collection!!).apply {
            component = Component.VTODO.name
            uid = "exdate-allday-test@test"
            summary = "All-day task with exception"
            dtstart = 1744972800000L  // 2025-04-19T00:00:00Z
            dtstartTimezone = JtxContract.JtxICalObject.TZ_ALLDAY
            exdate = "1745059200000"  // 2025-04-20T00:00:00Z (exception date)
        }

        // Generate iCalendar VTODO from data object
        val ical = obj.getICalendarFormat(testProdId)
        assertNotNull(ical)

        // Extract VTODO
        val vtodos = ical!!.getComponents<VToDo>(Component.VTODO.name)
        assertEquals(1, vtodos.size)

        // Verify ExDate property has VALUE=DATE parameter
        val exdateProp = vtodos[0].getProperty<ExDate<*>>(Property.EXDATE)
        assertTrue("ExDate property should be present", exdateProp.isPresent)
        
        val exdate = exdateProp.get()
        // Check that the ExDate has VALUE=DATE parameter
        assertEquals("ExDate should have VALUE=DATE parameter for all-day events", 
            Value.DATE, exdate.getParameter<Value>(Parameter.VALUE).get())
    }

    @Test
    fun getICalendarFormat_ExDateWithTimezone_noValueDateParameter() {
        // Test that ExDate for events with timezone does NOT include VALUE=DATE parameter
        val obj = JtxICalObject(collection!!).apply {
            component = Component.VTODO.name
            uid = "exdate-timezone-test@test"
            summary = "Task with exception and timezone"
            dtstart = 1744972800000L  // 2025-04-19T00:00:00Z
            dtstartTimezone = "Europe/Vienna"
            exdate = "1745059200000"  // 2025-04-20T00:00:00Z (exception date)
        }

        // Generate iCalendar VTODO from data object
        val ical = obj.getICalendarFormat(testProdId)
        assertNotNull(ical)

        // Extract VTODO
        val vtodos = ical!!.getComponents<VToDo>(Component.VTODO.name)
        assertEquals(1, vtodos.size)

        // Verify ExDate property does NOT have VALUE=DATE parameter (should use default)
        val exdateProp = vtodos[0].getProperty<ExDate<*>>(Property.EXDATE)
        assertTrue("ExDate property should be present", exdateProp.isPresent)
        
        val exdate = exdateProp.get()
        // For non-all-day events with timezone, VALUE parameter should not be DATE
        // (it will default to DATE-TIME or have a TZID parameter)
        assertEquals("ExDate for timezone events should not have VALUE=DATE", 
            null, exdate.getParameter<Value>(Parameter.VALUE).orElse(null))
    }


    /**
     * This function takes a file asserts if the ICalendar is the same before and after processing with getIncomingIcal and getOutgoingIcal
     * @param filename the filename to be processed
     * @param exceptions a list of property names that should not cause the assertion to fail (DTSTAMP is taken in any case)
     */
    private fun compare_properties(filename: String, exceptions: List<String>?) {

        val iCalIn = getIncomingIcal(filename)
        val iCalOut = getOutgoingIcal(filename)

        //assertEquals(iCalIn.components[0].getProperty(Component.VTODO), iCalOut.components[0].getProperty(Component.VTODO))

        // there should only be one component for VJOURNAL and VTODO!
        for(i in 0 until iCalIn.componentList.all.size)  {

            iCalIn.componentList.all[i].propertyList.all.forEach { inProp ->

                if(inProp.name == "DTSTAMP" || exceptions?.contains(inProp.name) == true)
                    return@forEach
                val outProp = iCalOut.componentList.all[i].propertyList.getFirst<Property>(inProp.name)?.getOrNull()

                if(inProp.name == "PRIORITY" && inProp.value == "0") {
                    assertNull(outProp)
                    return@forEach
                }

                assertEquals(inProp, outProp)
            }
        }
    }


    /**
     * This function takes a file and returns the parsed ical4j Calendar object
     * @param filename: The filename of the ics-file
     * @return the ICalendar with the parsed information from the file
     */
    private fun getIncomingIcal(filename: String): Calendar {

        val stream = javaClass.classLoader!!.getResourceAsStream(filename)
        val reader = InputStreamReader(stream, Charsets.UTF_8)

        val iCalIn = ICalendarParser().parse(reader)

        stream.close()
        reader.close()

        return iCalIn
    }

    /**
     * This function takes a filename and creates a JtxICalObject.
     * Then it uses the object to create an ical4j Calendar again.
     * @param filename: The filename of the ics-file
     * @return The ICalendar after applying all functionalities of JtxICalObject.fromReader(...)
     */
    private fun getOutgoingIcal(filename: String): Calendar {

        val stream = javaClass.classLoader!!.getResourceAsStream(filename)
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val iCalObject = at.bitfire.ical4android.JtxICalObject.fromReader(reader, collection!!)

        val os = ByteArrayOutputStream()

        iCalObject[0].write(os, testProdId)

        val iCalOut = ICalendarParser().parse(os.toByteArray().inputStream().reader())

        stream.close()
        reader.close()

        return iCalOut
    }

}
