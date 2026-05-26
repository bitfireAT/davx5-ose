/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Relation
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.parameter.RelatedType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationHandlerTest {

    @Test
    fun testName_Empty() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            putNull(Relation.NAME)
        }, contact)
        assertTrue(contact.relations.isEmpty())
    }

    @Test
    fun testName_Text() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "A Friend")
            put(Relation.TYPE, Relation.TYPE_FRIEND)
        }, contact)
        assertEquals("A Friend", contact.relations[0].text)
    }


    @Test
    fun testType_Assistant() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_ASSISTANT)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.ASSISTANT, RelatedType.CO_WORKER), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Brother() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_BROTHER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.BROTHER, RelatedType.SIBLING), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Child() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_CHILD)
        }, contact)
        assertArrayEquals(arrayOf(RelatedType.CHILD), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_CustomNoLabel() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_CUSTOM)
        }, contact)
        assertTrue(contact.relations[0].types.isEmpty())
    }

    @Test
    fun testType_CustomWithLabel() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_CUSTOM)
            put(Relation.LABEL, "Consultant")
        }, contact)
        assertArrayEquals(arrayOf(RelatedType.get("consultant")), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_DomesticPartner() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_DOMESTIC_PARTNER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.DOMESTIC_PARTNER, RelatedType.SPOUSE), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Father() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_FATHER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.FATHER, RelatedType.PARENT), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Friend() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_FRIEND)
        }, contact)
        assertArrayEquals(arrayOf(RelatedType.FRIEND), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Manager() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_MANAGER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.MANAGER, RelatedType.CO_WORKER), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Mother() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_MOTHER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.MOTHER, RelatedType.PARENT), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Parent() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_PARENT)
        }, contact)
        assertArrayEquals(arrayOf(RelatedType.PARENT), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Partner() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_PARTNER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.PARTNER), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_ReferredBy() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_REFERRED_BY)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.REFERRED_BY), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Relative() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_RELATIVE)
        }, contact)
        assertArrayEquals(arrayOf(RelatedType.KIN), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Sister() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_SISTER)
        }, contact)
        assertArrayEquals(arrayOf(CustomType.Related.SISTER, RelatedType.SIBLING), contact.relations[0].types.toTypedArray())
    }

    @Test
    fun testType_Spouse() {
        val contact = Contact()
        RelationHandler.handle(ContentValues().apply {
            put(Relation.NAME, "Somebody")
            put(Relation.TYPE, Relation.TYPE_SPOUSE)
        }, contact)
        assertArrayEquals(arrayOf(RelatedType.SPOUSE), contact.relations[0].types.toTypedArray())
    }

}