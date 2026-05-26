/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.property.Nickname
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NicknameBuilderTest {

    @Test
    fun testEmpty() {
        NicknameBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testLabel() {
        val c = Contact().apply {
            nickName = LabeledProperty(Nickname().apply {
                values.add("Nick 1")
                type = CustomType.Nickname.SHORT_NAME       // will be ignored because there's a label
            }, "Label 1")
        }
        NicknameBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("Nick 1", result[0].values[CommonDataKinds.Nickname.NAME])
            assertEquals(CommonDataKinds.Nickname.TYPE_CUSTOM, result[0].values[CommonDataKinds.Nickname.TYPE])
            assertEquals("Label 1", result[0].values[CommonDataKinds.Nickname.LABEL])
        }
    }


    @Test
    fun testMimeType() {
        val c = Contact().apply {
            nickName = LabeledProperty(Nickname().apply {
                values.add("Name 1")
            })
        }
        NicknameBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, result[0].values[CommonDataKinds.Nickname.MIMETYPE])
        }
    }


    @Test
    fun testType_Initials() {
        val c = Contact().apply {
            nickName = LabeledProperty(Nickname().apply {
                values.add("N1")
                type = CustomType.Nickname.INITIALS
            })
        }
        NicknameBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("N1", result[0].values[CommonDataKinds.Nickname.NAME])
            assertEquals(CommonDataKinds.Nickname.TYPE_INITIALS, result[0].values[CommonDataKinds.Nickname.TYPE])
        }
    }

    @Test
    fun testType_MaidenName() {
        val c = Contact().apply {
            nickName = LabeledProperty(Nickname().apply {
                values.add("Mai Den")
                type = CustomType.Nickname.MAIDEN_NAME
            })
        }
        NicknameBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("Mai Den", result[0].values[CommonDataKinds.Nickname.NAME])
            assertEquals(CommonDataKinds.Nickname.TYPE_MAIDEN_NAME, result[0].values[CommonDataKinds.Nickname.TYPE])
        }
    }

    @Test
    fun testType_ShortName() {
        val c = Contact().apply {
            nickName = LabeledProperty(Nickname().apply {
                values.add("Short Name")
                type = CustomType.Nickname.SHORT_NAME
            })
        }
        NicknameBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("Short Name", result[0].values[CommonDataKinds.Nickname.NAME])
            assertEquals(CommonDataKinds.Nickname.TYPE_SHORT_NAME, result[0].values[CommonDataKinds.Nickname.TYPE])
        }
    }


    @Test
    fun testValue_TwoValues() {
        val c = Contact().apply {
            nickName = LabeledProperty(Nickname().apply {
                values.add("Nick 1")
                values.add("Nick 2")
            })
        }
        NicknameBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(2, result.size)
            assertEquals("Nick 1", result[0].values[CommonDataKinds.Nickname.NAME])
            assertEquals("Nick 2", result[1].values[CommonDataKinds.Nickname.NAME])
        }
    }

}