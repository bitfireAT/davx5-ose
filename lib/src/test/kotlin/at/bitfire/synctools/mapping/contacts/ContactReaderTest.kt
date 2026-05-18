/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts

import at.bitfire.synctools.mapping.contacts.Contact.Downloader
import at.bitfire.synctools.vcard.property.CustomType
import at.bitfire.synctools.vcard.property.XAbDate
import at.bitfire.synctools.vcard.property.XAbLabel
import at.bitfire.synctools.vcard.property.XAbRelatedNames
import at.bitfire.synctools.vcard.property.XAddressBookServerKind
import at.bitfire.synctools.vcard.property.XPhoneticFirstName
import at.bitfire.synctools.vcard.property.XPhoneticLastName
import at.bitfire.synctools.vcard.property.XPhoneticMiddleName
import at.bitfire.synctools.vcard.property.XSip
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.parameter.RelatedType
import ezvcard.parameter.SoundType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.FormattedName
import ezvcard.property.Impp
import ezvcard.property.Kind
import ezvcard.property.Label
import ezvcard.property.Logo
import ezvcard.property.Member
import ezvcard.property.Nickname
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.ProductId
import ezvcard.property.RawProperty
import ezvcard.property.Related
import ezvcard.property.Revision
import ezvcard.property.SortString
import ezvcard.property.Sound
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Uid
import ezvcard.property.Url
import ezvcard.util.PartialDate
import ezvcard.util.TelUri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.time.LocalDate

class ContactReaderTest {

    // test specific fields

    @Test
    fun testAddress() = runTest {
        val address = Address().apply {
            streetAddress = "Street 101"
            country = "XX"
        }
        val c = ContactReader.fromVCard(VCard().apply {
            addAddress(address)
        })
        assertEquals(LabeledProperty(address), c.addresses.first)
    }

    @Test
    fun testAddressLabel_vCard3() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addOrphanedLabel(Label("Formatted Address"))
        })
        assertEquals(0, c.addresses.size)
        assertNull(c.unknownProperties)
    }



    @Test
    fun testAnniversary() = runTest {
        val instant = LocalDate.of(101, 6, 30)
        val c = ContactReader.fromVCard(VCard().apply {
            anniversary = Anniversary(instant)
        })
        assertEquals(Anniversary(instant), c.anniversary)
    }


    @Test
    fun testBirthday_Date() = runTest {
        val instant = LocalDate.of(101, 6, 30)
        val c = ContactReader.fromVCard(VCard().apply {
            birthday = Birthday(instant)
        })
        assertEquals(Birthday(instant), c.birthDay)
    }

    @Test
    fun testBirthday_vCard3_PartialDate() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            birthday = Birthday(LocalDate.of(1900, 7, 30)).apply {
                addParameter(Contact.DATE_PARAMETER_OMIT_YEAR, "1900")
            }
        })
        assertEquals(Birthday(PartialDate.parse("--0730")), c.birthDay)
    }

    @Test
    fun testBirthday_vCard4_PartialDate() = runTest {
        val b = Birthday(PartialDate.parse("--0730"))
        val c = ContactReader.fromVCard(VCard().apply {
            birthday = b
        })
        assertEquals(b, c.birthDay)
    }


    @Test
    fun testCategories() = runTest {
        val cat = Categories().apply {
            values.add("Cat1")
            values.add("Cat2")
        }
        val c = ContactReader.fromVCard(VCard().apply {
            addCategories(cat)
        })
        assertArrayEquals(arrayOf("Cat1", "Cat2"), c.categories.toTypedArray())
    }


    @Test
    fun testEmail() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addEmail("test@example.com")
        })
        assertEquals("test@example.com", c.emails.first.property.value)
    }


    @Test
    fun testFn() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            formattedName = FormattedName("Formatted Name")
        })
        assertEquals("Formatted Name", c.displayName)
    }


    @Test
    fun testImpp_Xmpp() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addImpp(Impp.xmpp("test@example.com"))
        })
        assertEquals(URI("xmpp:test@example.com"), c.impps.first.property.uri)
    }

    @Test
    fun testImpp_XSip() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XSip("test@example.com"))
        })
        assertEquals(URI("sip:test@example.com"), c.impps.first.property.uri)
    }


    @Test
    fun testKind_Group() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind.group()
        })
        assertTrue(c.group)
    }

    @Test
    fun testKind_Group_Uppercase() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind("GROUP")
        })
        assertTrue(c.group)
    }

    @Test
    fun testKind_Individual() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind.individual()
        })
        assertFalse(c.group)
    }


    @Test
    fun testLogo_Url() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V4_0).apply {
            addLogo(Logo("https://example.com/logo.png", ImageType.PNG))
        })
        assertTrue(c.unknownProperties!!.contains("LOGO;MEDIATYPE=image/png:https://example.com/logo.png"))
    }

    @Test
    fun testLogo_Url_TooLarge() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V4_0).apply {
            addLogo(Logo(ByteArray(ContactReader.MAX_BINARY_DATA_SIZE + 1), ImageType.PNG))
        })
        assertNull(c.unknownProperties)
    }


    @Test
    fun testMember_Uid() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind.group()
            members += Member("member1")
        })
        assertEquals("member1", c.members.first())
    }

    @Test
    fun testMember_Uid_Empty() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind.group()
            members += Member("")
        })
        assertTrue(c.members.isEmpty())
    }

    @Test
    fun testMember_UrnUiid() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind.group()
            members += Member("urn:uuid:be829cf2-4244-42f8-bd4c-ab39b4b5fcd3")
        })
        assertEquals("be829cf2-4244-42f8-bd4c-ab39b4b5fcd3", c.members.first())
    }

    @Test
    fun testMember_UrnUiid_Empty() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            kind = Kind.group()
            members += Member("urn:uuid:")
        })
        assertTrue(c.members.isEmpty())
    }


    @Test
    fun testN() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            structuredName = StructuredName().apply {
                prefixes.add("P1.")
                prefixes.add("P2.")
                given = "Given"
                additionalNames.add("Middle1")
                additionalNames.add("Middle2")
                family = "Family"
                suffixes.add("S1")
                suffixes.add("S2")
            }
        })
        assertEquals("P1. P2.", c.prefix)
        assertEquals("Given", c.givenName)
        assertEquals("Middle1 Middle2", c.middleName)
        assertEquals("Family", c.familyName)
        assertEquals("S1 S2", c.suffix)
    }


    @Test
    fun testNickname() = runTest {
        val nick = Nickname().apply {
            values.add("Nick1")
            values.add("Nick2")
        }
        val c = ContactReader.fromVCard(VCard().apply {
            addNickname(nick)
        })
        assertEquals(nick, c.nickName?.property)
    }


    @Test
    fun testNote() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addNote("Note 1")
            addNote("Note 2")
        })
        assertEquals("Note 1\n\n\nNote 2", c.note)
    }


    @Test
    fun testOrganization() = runTest {
        val org = Organization().apply {
            values.add("Org")
            values.add("Dept")
        }
        val c = ContactReader.fromVCard(VCard().apply {
            setOrganization(org)
        })
        assertEquals(org, c.organization)
    }


    @Test
    fun testProdId() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            productId = ProductId("Test")
        })
        assertNull(c.unknownProperties)
    }


    @Test
    fun testRelated_Uri() = runTest {
        val rel = Related.email("112@example.com")
        rel.types.add(RelatedType.EMERGENCY)
        val c = ContactReader.fromVCard(VCard().apply {
            addRelated(rel)
        })
        assertEquals(rel, c.relations.first)
    }

    @Test
    fun testRelated_String() = runTest {
        val rel = Related().apply {
            text = "My Best Friend"
            types.add(RelatedType.FRIEND)
        }
        val c = ContactReader.fromVCard(VCard().apply {
            addRelated(rel)
        })
        assertEquals(rel, c.relations.first)
    }


    @Test
    fun testRev() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            revision = Revision.now()
        })
        assertNull(c.unknownProperties)
    }

    @Test
    fun testRev_Invalid() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addExtendedProperty("REV", "+invalid-format!")
        })
        assertNull(c.unknownProperties)
    }


    @Test
    fun testRole() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addRole("Job Description")
        })
        assertEquals("Job Description", c.jobDescription)
    }


    @Test
    fun testSortString() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V3_0).apply {
            sortString = SortString("Harten")
        })
        assertNull(c.unknownProperties)
    }


    @Test
    fun testSource() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V3_0).apply {
            addSource("https://example.com/sample.vcf")
        })
        assertNull(c.unknownProperties)
    }


    @Test
    fun testSound_Url() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V4_0).apply {
            addSound(Sound("https://example.com/ding.wav", SoundType.WAV))
        })
        assertTrue(c.unknownProperties!!.contains("SOUND;MEDIATYPE=audio/wav:https://example.com/ding.wav"))
    }

    @Test
    fun testSound_Url_TooLarge() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V4_0).apply {
            addSound(Sound(ByteArray(ContactReader.MAX_BINARY_DATA_SIZE + 1), SoundType.WAV))
        })
        assertNull(c.unknownProperties)
    }


    @Test
    fun testTelephone() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            // number of type TEXT
            addTelephoneNumber("+1 555 12345")
            // number of type URI
            addTelephoneNumber(Telephone(TelUri.parse("tel:123")))
        })
        assertEquals("+1 555 12345", c.phoneNumbers[0].property.text)
        assertEquals("123", c.phoneNumbers[1].property.uri.number)
    }


    @Test
    fun testTitle() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addTitle("Job Title")
        })
        assertEquals("Job Title", c.jobTitle)
    }


    @Test
    fun testUid() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            uid = Uid("12345")
        })
        assertEquals("12345", c.uid)
    }


    @Test
    fun testUnkownProperty_vCard3() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V3_0).apply {
            addProperty(RawProperty("FUTURE-PROPERTY", "12345"))
        })
        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:3.0\r\n" +
                "FUTURE-PROPERTY:12345\r\n" +
                "END:VCARD\r\n", c.unknownProperties)
    }

    @Test
    fun testUnkownProperty_vCard4() = runTest {
        val c = ContactReader.fromVCard(VCard(VCardVersion.V4_0).apply {
            addProperty(RawProperty("FUTURE-PROPERTY", "12345"))
        })
        assertEquals("BEGIN:VCARD\r\n" +
                "VERSION:4.0\r\n" +
                "FUTURE-PROPERTY:12345\r\n" +
                "END:VCARD\r\n", c.unknownProperties)
    }


    @Test
    fun testUrl() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            urls += Url("https://example.com")
        })
        assertEquals("https://example.com", c.urls.first.property.value)
    }


    @Test
    fun testXAbDate_WithoutLabel() = runTest {
        val date = LocalDate.of(101, 6, 30)
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbDate(date))
        })
        assertEquals(LabeledProperty(XAbDate(date)), c.customDates.first)
    }

    @Test
    fun testXAbDate_WithLabel_AppleAnniversary() = runTest {
        val date = LocalDate.of(101, 6, 30)
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbDate(date).apply { group = "test1" })
            addProperty(XAbLabel(XAbLabel.APPLE_ANNIVERSARY).apply { group = "test1" })
        })
        assertEquals(0, c.customDates.size)
        assertEquals(Anniversary(date), c.anniversary)
    }

    @Test
    fun testXAbDate_WithLabel_AppleOther() = runTest {
        val date = LocalDate.of(101, 6, 30)
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbDate(date).apply { group = "test1" })
            addProperty(XAbLabel(XAbLabel.APPLE_OTHER).apply { group = "test1" })
        })
        assertEquals(date, c.customDates.first.property.date)
        assertNull(c.customDates.first.label)
    }

    @Test
    fun testXAbDate_WithLabel_Custom() = runTest {
        val date = LocalDate.of(101, 6, 30)
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbDate(date).apply { group = "test1" })
            addProperty(XAbLabel("Test 1").apply { group = "test1" })
        })
        assertEquals(date, c.customDates.first.property.date)
        assertEquals("Test 1", c.customDates.first.label)
    }


    @Test
    fun testXAbRelatedNames_Sister() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbRelatedNames("My Sis").apply { group = "item1" })
            addProperty(XAbLabel(XAbRelatedNames.APPLE_SISTER).apply { group = "item1" })
        })
        assertEquals("My Sis", c.relations.first.text)
        assertTrue(c.relations.first.types.contains(RelatedType.SIBLING))
        assertTrue(c.relations.first.types.contains(CustomType.Related.SISTER))
    }

    @Test
    fun testXAbRelatedNames_Custom() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbRelatedNames("Someone Other").apply { group = "item1" })
            addProperty(XAbLabel("Someone").apply { group = "item1" })
        })
        assertEquals("Someone Other", c.relations.first.text)
        assertEquals(1, c.relations.first.types.size)
        assertTrue(c.relations.first.types.contains(RelatedType.get("someone")))
    }

    @Test
    fun testXAbRelatedNames_Custom_Acquitance() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbRelatedNames("Someone Other").apply { group = "item1" })
            addProperty(XAbLabel(RelatedType.ACQUAINTANCE.value).apply { group = "item1" })
        })
        assertEquals("Someone Other", c.relations.first.text)
        assertEquals(1, c.relations.size)
        assertTrue(c.relations.first.types.contains(RelatedType.ACQUAINTANCE))
    }

    @Test
    fun testXAbRelatedNames_Custom_TwoValues() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAbRelatedNames("Someone Other").apply { group = "item1" })
            addProperty(XAbLabel("dog, cat").apply { group = "item1" })
        })
        assertEquals("Someone Other", c.relations.first.text)
        assertEquals(2, c.relations.first.types.size)
        assertTrue(c.relations.first.types.contains(RelatedType.get("cat")))
        assertTrue(c.relations.first.types.contains(RelatedType.get("dog")))
    }


    @Test
    fun testXAddressBookServerKind_Group() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAddressBookServerKind(Kind.GROUP))
        })
        assertTrue(c.group)
    }

    @Test
    fun testXAddressBookServerKind_Individual() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XAddressBookServerKind(Kind.INDIVIDUAL))
        })
        assertFalse(c.group)
    }


    @Test
    fun testXPhoneticName() = runTest {
        val c = ContactReader.fromVCard(VCard().apply {
            addProperty(XPhoneticFirstName("First"))
            addProperty(XPhoneticMiddleName("Middle"))
            addProperty(XPhoneticLastName("Last"))
        })
        assertEquals("First", c.phoneticGivenName)
        assertEquals("Middle", c.phoneticMiddleName)
        assertEquals("Last", c.phoneticFamilyName)
    }


    // test helper methods

    @Test
    fun testCheckPartialDate_Date_WithoutOmitYear() {
        val date = LocalDate.of(101, 6, 30)
        val withDate = Anniversary(date)
        ContactReader.checkPartialDate(withDate)
        assertEquals(date, withDate.date)
        assertNull(withDate.partialDate)
    }

    @Test
    fun testCheckPartialDate_Date_WithOmitYear_AnotherYear() {
        val date = LocalDate.of(10, 6, 30)
        val withDate = Anniversary(date).apply {
            addParameter(Contact.DATE_PARAMETER_OMIT_YEAR, "2010")
        }
        ContactReader.checkPartialDate(withDate)
        assertEquals(date, withDate.date)
        assertNull(withDate.partialDate)
        assertEquals(0, withDate.parameters.size())     // the year didn't match; we don't need the omit-year parameter anymore
    }

    @Test
    fun testCheckPartialDate_Date_WithOmitYear_SameYear() {
        val date = LocalDate.of(2010, 7, 30)
        val withDate = Anniversary(date).apply {
            addParameter(Contact.DATE_PARAMETER_OMIT_YEAR, "2010")
        }
        ContactReader.checkPartialDate(withDate)
        assertNull(withDate.date)
        assertEquals(PartialDate.parse("--0730"), withDate.partialDate)
        assertEquals(0, withDate.parameters.size())
    }

    @Test
    fun testCheckPartialDate_PartialDate() {
        val partialDate = PartialDate.parse("--0730")
        val withDate = Anniversary(partialDate)
        ContactReader.checkPartialDate(withDate)
        assertNull(withDate.date)
        assertEquals(partialDate, withDate.partialDate)
    }


    @Test
    fun testFindAndRemoveLabel_NoLabel() {
        val c = ContactReader(VCard())
        assertNull(c.findAndRemoveLabel("item1"))
    }

    @Test
    fun testFindAndRemoveLabel_Label() {
        val vCard = VCard().apply {
            addProperty(XAbLabel("Test Label").apply { group = "item1" })
        }
        val c = ContactReader(vCard)
        assertEquals("Test Label", vCard.getProperty(XAbLabel::class.java).value)
        assertEquals("Test Label", c.findAndRemoveLabel("item1"))
        assertNull(vCard.getProperty(XAbLabel::class.java))
    }

    @Test
    fun testFindAndRemoveLabel_Label_Empty() {
        val vCard = VCard().apply {
            addProperty(XAbLabel("").apply { group = "item1" })
        }
        val c = ContactReader(vCard)
        assertEquals("", vCard.getProperty(XAbLabel::class.java).value)
        assertNull(c.findAndRemoveLabel("item1"))
        assertNull(vCard.getProperty(XAbLabel::class.java))
    }

    @Test
    fun testFindAndRemoveLabel_LabelWithOtherGroup() {
        val vCard = VCard().apply {
            addProperty(XAbLabel("Test Label").apply { group = "item1" })
        }
        val c = ContactReader(vCard)
        assertEquals("Test Label", vCard.getProperty(XAbLabel::class.java).value)
        assertNull(c.findAndRemoveLabel("item2"))
        assertEquals("Test Label", vCard.getProperty(XAbLabel::class.java).value)
    }


    @Test
    fun testGetPhotoBytes_Binary() = runTest {
        val sample = ByteArray(128)
        assertEquals(sample, ContactReader.fromVCard(VCard().apply {
            addPhoto(Photo(sample, ImageType.JPEG))
        }).photo)
    }

    @Test
    fun testGetPhotoBytes_Downloader() = runTest {
        val sample = ByteArray(128)
        val sampleUrl = "http://example.com/photo.jpg"
        val downloader = object: Downloader {
            override suspend fun download(url: String, accepts: String): ByteArray? {
                return if (url == sampleUrl && accepts == "image/*")
                    sample
                else
                    null
            }
        }
        assertEquals(sample, ContactReader.fromVCard(VCard().apply {
            addPhoto(Photo(sampleUrl, ImageType.JPEG))
        }, downloader).photo)
    }


    @Test
    fun testUriToUid() {
        assertEquals("uid", ContactReader.uriToUid("uid"))
        assertEquals("urn:uid", ContactReader.uriToUid("urn:uid"))
        assertEquals("12345", ContactReader.uriToUid("urn:uuid:12345"))
        assertNull(ContactReader.uriToUid(""))
        assertNull(ContactReader.uriToUid("urn:uuid:"))
    }

}