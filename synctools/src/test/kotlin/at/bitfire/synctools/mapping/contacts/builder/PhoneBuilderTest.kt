/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.builder

import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.LabeledProperty
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.parameter.TelephoneType
import ezvcard.property.Telephone
import ezvcard.util.TelUri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhoneBuilderTest {

    @Test
    fun testEmpty() {
        PhoneBuilder(Uri.EMPTY, null, Contact(), false).build().also { result ->
            assertEquals(0, result.size)
        }
    }


    @Test
    fun testNumber_Blank() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone(""))
        }, false).build().also { result ->
            assertEquals(0, result.size)
        }
    }

    @Test
    fun testNumber_Value_Text() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345"))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("+1 555 12345", result[0].values[CommonDataKinds.Phone.NUMBER])
        }
    }

    @Test
    fun testNumber_Value_Uri() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone(TelUri.parse("tel:+1 555 12345")))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("+1 555 12345", result[0].values[CommonDataKinds.Phone.NUMBER])
        }
    }

    @Test
    fun testNumber_Value_Uri_WithExtension() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone(TelUri.Builder("+15551234567").extension("5555").build()))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("+15551234567;5555", result[0].values[CommonDataKinds.Phone.NUMBER])
        }
    }

    @Test
    fun testNumber_Value_Uri_BlankExtension() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone(TelUri.Builder("+15551234567").build()))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("+15551234567", result[0].values[CommonDataKinds.Phone.NUMBER])
        }
    }

    @Test
    fun testNumber_Value_Uri_AlreadyHasSeparator() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone(TelUri.parse("tel:+15551234567,5555;ext=5555")))
        }, false).build().also { result ->
            assertEquals(1, result.size)
            assertEquals("+15551234567,5555", result[0].values[CommonDataKinds.Phone.NUMBER])
        }
    }


    @Test
    fun testLabel() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345"), "Label")
        }, false).build().also { result ->
            assertEquals("Label", result[0].values[CommonDataKinds.Phone.LABEL])
        }
    }


    @Test
    fun testMimeType() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345"))
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, result[0].values[CommonDataKinds.Phone.MIMETYPE])
        }
    }


    @Test
    fun testPref_None() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345"))
        }, false).build().also { result ->
            assertEquals(0, result[0].values[CommonDataKinds.Phone.IS_PRIMARY])
        }
    }

    @Test
    fun testPref_1() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                pref = 1
            })
        }, false).build().also { result ->
            assertEquals(1, result[0].values[CommonDataKinds.Phone.IS_PRIMARY])
        }
    }


    @Test
    fun testType_Assistant() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(CustomType.Phone.ASSISTANT)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_ASSISTANT, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Callback() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(CustomType.Phone.CALLBACK)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_CALLBACK, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Cell() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.CELL)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_MOBILE, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_CellWork() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.CELL)
                types.add(TelephoneType.WORK)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_WORK_MOBILE, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_CompanyName() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(CustomType.Phone.COMPANY_MAIN)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_COMPANY_MAIN, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Fax() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.FAX)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_OTHER_FAX, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_FaxHome() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.FAX)
                types.add(TelephoneType.HOME)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_FAX_HOME, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_FaxWork() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.FAX)
                types.add(TelephoneType.WORK)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_FAX_WORK, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Home() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.HOME)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_HOME, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Isdn() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.ISDN)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_ISDN, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Mms() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(CustomType.Phone.MMS)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_MMS, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_NotInAndroid() {
        // some type which is not mapped in Android
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.VIDEO)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_OTHER, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Pager() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.PAGER)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_PAGER, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_PagerWork() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.PAGER)
                types.add(TelephoneType.WORK)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_WORK_PAGER, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Radio() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(CustomType.Phone.RADIO)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_RADIO, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

    @Test
    fun testType_Work() {
        PhoneBuilder(Uri.EMPTY, null, Contact().apply {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345").apply {
                types.add(TelephoneType.WORK)
            })
        }, false).build().also { result ->
            assertEquals(CommonDataKinds.Phone.TYPE_WORK, result[0].values[CommonDataKinds.Phone.TYPE])
        }
    }

}
