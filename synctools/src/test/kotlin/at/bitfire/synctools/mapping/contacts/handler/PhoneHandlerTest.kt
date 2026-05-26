/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Phone
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.parameter.TelephoneType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneHandlerTest {

    @Test
    fun testIsPrimary_False() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.IS_PRIMARY, 0)
        }, contact)
        assertNull(contact.phoneNumbers[0].property.pref)
    }

    @Test
    fun testIsPrimary_True() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.IS_PRIMARY, 1)
        }, contact)
        assertEquals(1, contact.phoneNumbers[0].property.pref)
    }


    @Test
    fun testNumber_Empty() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            putNull(Phone.NUMBER)
        }, contact)
        assertTrue(contact.phoneNumbers.isEmpty())
    }

    @Test
    fun testNumber_Value() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
        }, contact)
        assertEquals(1, contact.phoneNumbers.size)
        assertEquals("+1 555 12345", contact.phoneNumbers[0].property.text)
    }


    @Test
    fun testType_Assistant() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_ASSISTANT)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Phone.ASSISTANT), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Callback() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_CALLBACK)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Phone.CALLBACK), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Car() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_CAR)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.CAR), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_CompanyName() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_COMPANY_MAIN)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Phone.COMPANY_MAIN), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_FaxHome() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_FAX_HOME)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.FAX, TelephoneType.HOME), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_FaxOther() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_OTHER_FAX)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.FAX), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_FaxWork() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_FAX_WORK)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.FAX, TelephoneType.WORK), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Home() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_HOME)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.HOME), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Isdn() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_ISDN)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.ISDN), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Mms() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_MMS)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Phone.MMS), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Pager() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_PAGER)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.PAGER), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_PagerWork() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_WORK_PAGER)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.PAGER, TelephoneType.WORK), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Radio() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_RADIO)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Phone.RADIO), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_Work() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_WORK)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.WORK), contact.phoneNumbers[0].property.types.toTypedArray())
    }

    @Test
    fun testType_WorkMobile() {
        val contact = Contact()
        PhoneHandler.handle(ContentValues().apply {
            put(Phone.NUMBER, "+1 555 12345")
            put(Phone.TYPE, Phone.TYPE_WORK_MOBILE)
        }, contact)
        assertArrayEquals(arrayOf(TelephoneType.CELL, TelephoneType.WORK), contact.phoneNumbers[0].property.types.toTypedArray())
    }

}