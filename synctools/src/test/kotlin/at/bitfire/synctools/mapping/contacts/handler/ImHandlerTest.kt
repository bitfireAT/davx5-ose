/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Im
import at.bitfire.synctools.mapping.contacts.Contact
import ezvcard.parameter.ImppType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImHandlerTest {

    @Test
    fun testHandle_Empty() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
        }, contact)
        assertTrue(contact.impps.isEmpty())
    }

    @Test
    fun testHandle_Value() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "messenger-id")
        }, contact)
        assertEquals("messenger-id", contact.impps[0].property.handle)
    }


    @Test
    fun testProtocol_Custom() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "id")
        }, contact)
        assertEquals("new-messenger", contact.impps[0].property.protocol)
    }

    @Test
    fun testProtocol_Empty() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            putNull(Im.PROTOCOL)
        }, contact)
        assertTrue(contact.impps.isEmpty())
    }

    @Test
    fun testProtocol_Legacy_Aim() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_AIM)
            put(Im.DATA, "aim-id")
        }, contact)
        assertEquals("aim", contact.impps[0].property.protocol)
    }


    @Test
    fun testTypeCustom_NoLabel() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "messenger-id")
            put(Im.TYPE, Im.TYPE_CUSTOM)
        }, contact)
        assertTrue(contact.impps[0].property.types.isEmpty())
        assertNull(contact.impps[0].label)
    }

    @Test
    fun testTypeCustom_WithLabel() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "messenger-id")
            put(Im.TYPE, Im.TYPE_CUSTOM)
            put(Im.LABEL, "New Messenger")
        }, contact)
        assertTrue(contact.impps[0].property.types.isEmpty())
        assertEquals("New Messenger", contact.impps[0].label)
    }

    @Test
    fun testType_Home() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "messenger-id")
            put(Im.TYPE, Im.TYPE_HOME)
        }, contact)
        assertEquals(ImppType.HOME, contact.impps[0].property.types[0])
    }

    @Test
    fun testType_Other() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "messenger-id")
            put(Im.TYPE, Im.TYPE_OTHER)
        }, contact)
        assertTrue(contact.impps[0].property.types.isEmpty())
    }

    @Test
    fun testType_Work() {
        val contact = Contact()
        ImHandler.handle(ContentValues().apply {
            put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM)
            put(Im.CUSTOM_PROTOCOL, "new-messenger")
            put(Im.DATA, "messenger-id")
            put(Im.TYPE, Im.TYPE_WORK)
        }, contact)
        assertEquals(ImppType.WORK, contact.impps[0].property.types[0])
    }


    // tests for helpers

    @Test
    fun testProtocolToUriScheme() {
        assertNull(ImHandler.protocolToUriScheme(null))
        assertEquals("", ImHandler.protocolToUriScheme(""))
        assertEquals("protocol", ImHandler.protocolToUriScheme("PrO/ätO\\cOl"))
    }

}