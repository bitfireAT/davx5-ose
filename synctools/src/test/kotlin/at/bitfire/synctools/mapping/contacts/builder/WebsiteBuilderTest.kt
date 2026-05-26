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
import ezvcard.property.Url
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebsiteBuilderTest {

    @Test
    fun testEmpty() {
        WebsiteBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testUrl_Empty() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url(""))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testUrl_Value() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com"))
        }, false).build().also { result ->
            assertEquals("https://example.com", result[0].values[CommonDataKinds.Website.URL])
        }
    }


    @Test
    fun testLabel() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com"), "Label")
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Im.TYPE_CUSTOM, result[0].values[CommonDataKinds.Website.TYPE])
            assertEquals("Label", result[0].values[CommonDataKinds.Website.LABEL])
        }
    }


    @Test
    fun testMimeType() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com"))
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.CONTENT_ITEM_TYPE, result[0].values[CommonDataKinds.Website.MIMETYPE])
        }
    }


    @Test
    fun testType_Blog() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com").apply {
                type = CustomType.Url.TYPE_BLOG
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_BLOG, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_Ftp() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("ftps://example.com").apply {
                type = CustomType.Url.TYPE_FTP
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_FTP, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_Home() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com").apply {
                type = CustomType.HOME
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_HOME, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_Homepage() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com").apply {
                type = CustomType.Url.TYPE_HOMEPAGE
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_HOMEPAGE, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_None() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("ftps://example.com"))
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_OTHER, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_Profile() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com").apply {
                type = CustomType.Url.TYPE_PROFILE
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_PROFILE, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

    @Test
    fun testType_Work() {
        WebsiteBuilder(Uri.EMPTY, null, Contact().apply {
            urls += LabeledProperty(Url("https://example.com").apply {
                type = CustomType.WORK
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Website.TYPE_WORK, result[0].values[CommonDataKinds.Im.TYPE])
        }
    }

}