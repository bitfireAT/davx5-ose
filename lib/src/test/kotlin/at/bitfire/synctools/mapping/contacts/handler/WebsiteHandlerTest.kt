/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Website
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.vcard.property.CustomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebsiteHandlerTest {

    @Test
    fun testUrl_Empty() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            putNull(Website.URL)
        }, contact)
        assertTrue(contact.urls.isEmpty())
    }

    @Test
    fun testUrl_Value() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
        }, contact)
        assertEquals("https://example.com", contact.urls[0].property.value)
    }


    @Test
    fun testType_Blog() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_BLOG)
        }, contact)
        assertEquals(CustomType.Url.TYPE_BLOG, contact.urls[0].property.type)
    }

    @Test
    fun testType_Custom_Label() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_CUSTOM)
            put(Website.LABEL, "Label")
        }, contact)
        assertNull(contact.urls[0].property.type)
        assertEquals("Label", contact.urls[0].label)
    }

    @Test
    fun testType_Custom_NoLabel() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_CUSTOM)
        }, contact)
        assertNull(contact.urls[0].property.type)
    }

    @Test
    fun testType_Ftp() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_FTP)
        }, contact)
        assertEquals(CustomType.Url.TYPE_FTP, contact.urls[0].property.type)
    }

    @Test
    fun testType_Home() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_HOME)
        }, contact)
        assertEquals(CustomType.HOME, contact.urls[0].property.type)
    }

    @Test
    fun testType_Homepage() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_HOMEPAGE)
        }, contact)
        assertEquals(CustomType.Url.TYPE_HOMEPAGE, contact.urls[0].property.type)
    }

    @Test
    fun testType_Profile() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_PROFILE)
        }, contact)
        assertEquals(CustomType.Url.TYPE_PROFILE, contact.urls[0].property.type)
    }

    @Test
    fun testType_Work() {
        val contact = Contact()
        WebsiteHandler.handle(ContentValues().apply {
            put(Website.URL, "https://example.com")
            put(Website.TYPE, Website.TYPE_WORK)
        }, contact)
        assertEquals(CustomType.WORK, contact.urls[0].property.type)
    }

}