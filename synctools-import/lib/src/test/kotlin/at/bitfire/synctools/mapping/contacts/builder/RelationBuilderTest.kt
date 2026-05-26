/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Relation
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.parameter.RelatedType
import ezvcard.property.Related
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RelationBuilderTest {

    @Test
    fun testEmpty() {
        RelationBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testMimeType() {
        val c = Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.FRIEND
            }
        }
        RelationBuilder(Uri.EMPTY, null, c, false).build().also { result ->
            assertEquals(Relation.CONTENT_ITEM_TYPE, result[0].values[Relation.MIMETYPE])
        }
    }


    @Test
    fun testName_Text() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related().apply {
                text = "Somebody"
                types += RelatedType.FRIEND
            }
        }, false).build().also { result ->
            assertEquals("Somebody", result[0].values[Relation.NAME])
        }
    }

    @Test
    fun testName_TextAndUri() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("uri").apply {
                text = "Text"
                types += RelatedType.FRIEND
            }
        }, false).build().also { result ->
            assertEquals("Text", result[0].values[Relation.NAME])
        }
    }

    @Test
    fun testName_Uri() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.FRIEND
            }
        }, false).build().also { result ->
            assertEquals("somebody", result[0].values[Relation.NAME])
        }
    }


    @Test
    fun testType_Assistant() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.ASSISTANT
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_ASSISTANT, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Brother() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.BROTHER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_BROTHER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_BrotherAndSibling() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.SIBLING
                types += CustomType.Related.BROTHER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_BROTHER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Child() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.CHILD
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_CHILD, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_DomesticPartner() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.DOMESTIC_PARTNER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_DOMESTIC_PARTNER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Father() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.FATHER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_FATHER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_FatherAndParent() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.PARENT
                types += CustomType.Related.FATHER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_FATHER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Friend() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.FRIEND
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_FRIEND, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Kin() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.KIN
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_RELATIVE, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Manager() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.MANAGER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_MANAGER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Mother() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.MOTHER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_MOTHER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_MotherAndParent() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.PARENT
                types += CustomType.Related.MOTHER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_MOTHER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_NoAndroidMapping() {
        // some value that has no Android mapping
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.SWEETHEART
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_CUSTOM, result[0].values[Relation.TYPE])
            assertEquals("Sweetheart", result[0].values[Relation.LABEL])
        }
    }

    @Test
    fun testType_Parent() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.PARENT
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_PARENT, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Partner() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.PARTNER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_PARTNER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_ReferredBy() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.REFERRED_BY
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_REFERRED_BY, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Sister() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += CustomType.Related.SISTER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_SISTER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_SisterAndSibling() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.SIBLING
                types += CustomType.Related.SISTER
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_SISTER, result[0].values[Relation.TYPE])
        }
    }

    @Test
    fun testType_Spouse() {
        RelationBuilder(Uri.EMPTY, null, Contact().apply {
            relations += Related("somebody").apply {
                types += RelatedType.SPOUSE
            }
        }, false).build().also { result ->
            assertEquals(Relation.TYPE_SPOUSE, result[0].values[Relation.TYPE])
        }
    }

}