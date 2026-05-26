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
import ezvcard.parameter.ImppType
import ezvcard.property.Impp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImBuilderTest {

    @Test
    fun testEmpty() {
        ImBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testHandle_Empty() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp(""))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testHandle_WithoutProtocol() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp("test@example.com"))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testHandle_WithProtocol() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.xmpp("jabber@example.com"))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals(CommonDataKinds.Im.PROTOCOL_CUSTOM, result[0].values[CommonDataKinds.Im.PROTOCOL])
            assertEquals("xmpp", result[0].values[CommonDataKinds.Im.CUSTOM_PROTOCOL])
            assertEquals("jabber@example.com", result[0].values[CommonDataKinds.Im.DATA])
        }
    }


    @Test
    fun testIgnoreSip() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp("sip:voip@example.com"))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testLabel() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.xmpp("jabber@example.com"), "Label")
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Im.TYPE_CUSTOM, result[0].values[CommonDataKinds.Im.TYPE])
            assertEquals("Label", result[0].values[CommonDataKinds.Im.LABEL])
        }
    }


    @Test
    fun testMimeType() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.xmpp("jabber@example.com"))
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Im.CONTENT_ITEM_TYPE, result[0].values[CommonDataKinds.Im.MIMETYPE])
        }
    }


    @Test
    fun testProtocol_Sip() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.sip("voip@example.com"))
        }, false).build().also { result ->
            // handled by SipAddressHandler
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testType_Home() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.xmpp("jabber@example.com").apply {
                types.add(ImppType.HOME)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Im.TYPE_HOME, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_NotInAndroid() {
        // some vCard type that is not supported by Android
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.xmpp("jabber@example.com").apply {
                types.add(ImppType.MOBILE)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Im.TYPE_OTHER, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_Work() {
        ImBuilder(Uri.EMPTY, null, Contact().apply {
            impps += LabeledProperty(Impp.xmpp("jabber@example.com").apply {
                types.add(ImppType.WORK)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Im.TYPE_WORK, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

}