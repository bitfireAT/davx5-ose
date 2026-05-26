/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentValues
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.impl.TestJtxCollection
import at.bitfire.ical4android.impl.testProdId

import at.bitfire.synctools.test.GrantPermissionOrSkipRule
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.asSyncAdapter
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JtxCollectionTest {

    @get:Rule
    val permissionRule = GrantPermissionOrSkipRule(TaskProvider.PERMISSIONS_JTX.toSet())

    val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var client: ContentProviderClient

    private val testAccount = Account(javaClass.name, JtxContract.JtxCollection.TEST_ACCOUNT_TYPE)

    private val url = "https://jtx.techbee.at"
    private val displayname = "jtx"
    private val syncversion = JtxContract.VERSION

    private val cv = ContentValues().apply {
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
    }

    @After
    fun tearDown() {
        client.close()

        var collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        collections.forEach { collection ->
            collection.delete()
        }
        collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        assertEquals(0, collections.size)
    }


    @Test
    fun create_populate_find() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)
        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)

        assertEquals(1, collections.size)
        assertEquals(testAccount.type, collections[0].account.type)
        assertEquals(testAccount.name, collections[0].account.name)
        assertEquals(url, collections[0].url)
        assertEquals(displayname, collections[0].displayname)
        assertEquals(syncversion.toString(), collections[0].syncstate)
    }

    @Test
    fun queryICalObjects() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)

        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        val items = collections[0].queryICalObjects(null, null)
        assertEquals(0, items.size)

        val cv = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)
        val icalobjects = collections[0].queryICalObjects(null, null)

        assertEquals(1, icalobjects.size)
    }

    @Test
    fun queryRecur_test() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)

        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        val item = collections[0].queryRecur("abc1234", "xyz5678")
        assertNull(item)

        val cv = ContentValues().apply {
            put(JtxContract.JtxICalObject.UID, "abc1234")
            put(JtxContract.JtxICalObject.RECURID, "xyz5678")
            put(JtxContract.JtxICalObject.RECURID_TIMEZONE, "Europe/Vienna")
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv)
        val contentValues = collections[0].queryRecur("abc1234", "xyz5678")

        assertEquals("abc1234", contentValues?.getAsString(JtxContract.JtxICalObject.UID))
        assertEquals("xyz5678", contentValues?.getAsString(JtxContract.JtxICalObject.RECURID))
        assertEquals("Europe/Vienna", contentValues?.getAsString(JtxContract.JtxICalObject.RECURID_TIMEZONE))
    }

    @Test
    fun getICSForCollection_test() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)

        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)
        val items = collections[0].queryICalObjects(null, null)
        assertEquals(0, items.size)

        val cv1 = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "summary")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VJOURNAL.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        val cv2 = ContentValues().apply {
            put(JtxContract.JtxICalObject.SUMMARY, "entry2")
            put(JtxContract.JtxICalObject.COMPONENT, JtxContract.JtxICalObject.Component.VTODO.name)
            put(JtxContract.JtxICalObject.ICALOBJECT_COLLECTIONID, collections[0].id)
        }
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv1)
        client.insert(JtxContract.JtxICalObject.CONTENT_URI.asSyncAdapter(testAccount), cv2)

        val ics = collections[0].getICSForCollection(testProdId)
        assertTrue(ics.contains(Regex("BEGIN:VCALENDAR(\\n*|\\r*|\\t*|.*)*END:VCALENDAR")))
        System.err.println(ics)
        assertTrue(ics.contains("PRODID:${testProdId.value}"))
        assertTrue(ics.contains("SUMMARY:summary"))
        assertTrue(ics.contains("SUMMARY:entry2"))
        assertTrue(ics.contains(Regex("BEGIN:VJOURNAL(\\n*|\\r*|\\t*|.*)*END:VJOURNAL")))
        assertTrue(ics.contains(Regex("BEGIN:VTODO(\\n*|\\r*|\\t*|.*)*END:VTODO")))
    }


    @Test
    fun updateLastSync_test() {
        val collectionUri = JtxCollection.create(testAccount, client, cv)
        assertNotNull(collectionUri)
        val collections = JtxCollection.find(testAccount, client, context, TestJtxCollection.Factory, null, null)

        collections.forEach { collection ->
            client.query(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(testAccount), arrayOf(JtxContract.JtxCollection.LAST_SYNC), null, emptyArray(), null).use {
                assertNotNull(it)
                assertTrue(it!!.moveToFirst())
                assertTrue(it.isNull(0))
            }
            collection.updateLastSync()
            client.query(JtxContract.JtxCollection.CONTENT_URI.asSyncAdapter(testAccount), arrayOf(JtxContract.JtxCollection.LAST_SYNC), null, emptyArray(), null).use {
                assertNotNull(it)
                assertTrue(it!!.moveToFirst())
                assertTrue(!it.isNull(0))
            }
        }
    }
}
