/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

import at.bitfire.synctools.vcard.property.CustomType
import at.bitfire.synctools.vcard.property.XAbDate
import at.bitfire.synctools.vcard.property.XAbLabel
import at.bitfire.synctools.vcard.property.XAbRelatedNames
import at.bitfire.synctools.vcard.property.XAddressBookServerKind
import at.bitfire.synctools.vcard.property.XAddressBookServerMember
import at.bitfire.synctools.vcard.property.XPhoneticFirstName
import at.bitfire.synctools.vcard.property.XPhoneticLastName
import at.bitfire.synctools.vcard.property.XPhoneticMiddleName
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.parameter.RelatedType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Email
import ezvcard.property.Impp
import ezvcard.property.Kind
import ezvcard.property.Nickname
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.Related
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Url
import ezvcard.util.PartialDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter
import java.net.URI
import java.time.LocalDate

class ContactWriterTest {

    @Test
    fun testGenerate_SetsProductId() {
        val vCard = generate { }
        assertEquals(testProductId, vCard.productId.value)
    }


    // test specific fields

    @Test
    fun testAddress() {
        val address = Address().apply {
            streetAddress = "Test Street"
            country = "XX"
        }
        val vCard = generate {
            addresses.add(LabeledProperty(address))
        }
        assertEquals(address, vCard.addresses.first())
    }

    @Test
    fun testAddressCaretEncoding() {
        val address = Address()
        address.label = "My \"Label\"\nLine 2"
        address.streetAddress = "Street \"Address\""
        val contact = Contact()
        contact.addresses += LabeledProperty(address)

        /* label-param = "LABEL=" param-value
         * param-values must not contain DQUOTE and should be encoded as defined in RFC 6868
         *
         * ADR-value = ADR-component-pobox ";" ADR-component-ext ";"
         *             ADR-component-street ";" ADR-component-locality ";"
         *             ADR-component-region ";" ADR-component-code ";"
         *             ADR-component-country
         * ADR-component-pobox    = list-component
         *
         * list-component = component *("," component)
         * component = "\\" / "\," / "\;" / "\n" / WSP / NON-ASCII / %x21-2B / %x2D-3A / %x3C-5B / %x5D-7E
         *
         * So, ADR value components may contain DQUOTE (0x22) and don't have to be encoded as defined in RFC 6868 */

        val writer = StringWriter()
        ContactWriter(contact, VCardVersion.V4_0, testProductId).writeVCard(writer)
        assertTrue(writer.toString().contains("ADR;LABEL=My ^'Label^'\\nLine 2:;;Street \"Address\";;;;"))
    }


    @Test
    fun testAnniversary_vCard3() {
        val date = LocalDate.of(121, 6, 30)
        val vCard = generate(version = VCardVersion.V3_0) {
            anniversary = Anniversary(date)
        }
        assertNull(vCard.anniversary)
        assertEquals(date, vCard.getProperty(XAbDate::class.java).date)
    }

    @Test
    fun testAnniversary_vCard4() {
        val ann = Anniversary(LocalDate.of(121, 6, 30))
        val vCard = generate(version = VCardVersion.V4_0) {
            anniversary = ann
        }
        assertEquals(ann, vCard.anniversary)
    }


    @Test
    fun testBirthday() {
        val bday = Birthday(LocalDate.of(121, 6, 30))
        val vCard = generate {
            birthDay = bday
        }
        assertEquals(bday, vCard.birthday)
    }


    @Test
    fun testCustomDate() {
        val date = XAbDate(LocalDate.of(121, 6, 30))
        val vCard = generate {
            customDates += LabeledProperty(date)
        }
        assertEquals(date, vCard.getProperty(XAbDate::class.java))
    }


    @Test
    fun testCategories_Some() {
        val vCard = generate {
            categories += "cat1"
            categories += "cat2"
        }
        assertEquals("cat1", vCard.categories.values[0])
        assertEquals("cat2", vCard.categories.values[1])
    }

    @Test
    fun testCategories_None() {
        val vCard = generate { }
        assertNull(vCard.categories)
    }


    @Test
    fun testEmail() {
        val vCard = generate {
            emails.add(LabeledProperty(Email("test@example.com")))
        }
        assertEquals("test@example.com", vCard.emails.first().value)
    }


    @Test
    fun testFn_vCard3_NoFn_Organization() {
        val vCard = generate(version = VCardVersion.V3_0) {
            organization = Organization().apply {
                values.add("org")
                values.add("dept")
            }
            // other values should be ignored because organization is available
            nickName = LabeledProperty(Nickname().apply {
                values.add("nick1")
            })
        }
        assertEquals("org / dept", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard3_NoFn_NickName() {
        val vCard = generate(version = VCardVersion.V3_0) {
            nickName = LabeledProperty(Nickname().apply {
                values.add("nick1")
            })
            // other values should be ignored because nickname is available
            emails += LabeledProperty(Email("test@example.com"))
        }
        assertEquals("nick1", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard3_NoFn_Email() {
        val vCard = generate(version = VCardVersion.V3_0) {
            emails += LabeledProperty(Email("test@example.com"))
            // other values should be ignored because email is available
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345"))
        }
        assertEquals("test@example.com", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard3_NoFn_Phone() {
        val vCard = generate(version = VCardVersion.V3_0) {
            phoneNumbers += LabeledProperty(Telephone("+1 555 12345"))
            // other values should be ignored because phone is available
            uid = "uid"
        }
        assertEquals("+1 555 12345", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard3_NoFn_Uid() {
        val vCard = generate(version = VCardVersion.V3_0) {
            uid = "uid"
        }
        assertEquals("uid", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard3_NoFn_Nothing() {
        val vCard = generate(version = VCardVersion.V3_0) { }
        assertEquals("", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard3_Fn() {
        val vCard = generate(version = VCardVersion.V3_0) {
            displayName = "Display Name"
        }
        assertEquals("Display Name", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard4_NoFn() {
        val vCard = generate(version = VCardVersion.V4_0) { }
        assertEquals("", vCard.formattedName.value)
    }

    @Test
    fun testFn_vCard4_Fn() {
        val vCard = generate(version = VCardVersion.V4_0) {
            displayName = "Display Name"
        }
        assertEquals("Display Name", vCard.formattedName.value)
    }


    @Test
    fun testGroup_vCard3() {
        val vCard = generate(VCardVersion.V3_0) {
            group = true
            members += "member1"
            displayName = "Sample vCard3 Group"
        }
        assertEquals(Kind.GROUP, vCard.getProperty(XAddressBookServerKind::class.java).value)
        assertEquals("urn:uuid:member1", vCard.getProperty(XAddressBookServerMember::class.java).value)
        assertEquals("Sample vCard3 Group", vCard.formattedName.value)
        assertEquals(StructuredName().apply {
            family = "Sample vCard3 Group"
        }, vCard.structuredName)
    }

    @Test
    fun testGroup_vCard4() {
        val vCard = generate(VCardVersion.V4_0) {
            group = true
            members += "member1"
            displayName = "Sample vCard4 Group"
        }
        assertEquals(Kind.GROUP, vCard.getProperty(Kind::class.java).value)
        assertEquals("urn:uuid:member1", vCard.members.first().value)
        assertEquals("Sample vCard4 Group", vCard.formattedName.value)
        assertNull(vCard.structuredName)
    }


    @Test
    fun testImpp() {
        val vCard = generate {
            impps.add(LabeledProperty(Impp.xmpp("test@example.com")))
        }
        assertEquals(URI("xmpp:test@example.com"), vCard.impps.first().uri)
    }


    @Test
    fun testN_vCard3_NoN() {
        val vCard = generate(version = VCardVersion.V3_0) { }
        assertEquals(StructuredName(), vCard.structuredName)
    }

    @Test
    fun testN_vCard4_NoN() {
        val vCard = generate(version = VCardVersion.V4_0) { }
        assertNull(vCard.structuredName)
    }

    @Test
    fun testN() {
        val vCard = generate(version = VCardVersion.V4_0) {
            prefix = "P1. P2."
            givenName = "Given"
            middleName = "Middle1 Middle2"
            familyName = "Family"
            suffix = "S1 S2"
        }
        assertEquals(StructuredName().apply {
            prefixes += "P1."
            prefixes += "P2."
            given = "Given"
            additionalNames += "Middle1"
            additionalNames += "Middle2"
            family = "Family"
            suffixes += "S1"
            suffixes += "S2"
        }, vCard.structuredName)
    }


    @Test
    fun testNote() {
        val vCard = generate { note = "Some Note" }
        assertEquals("Some Note", vCard.notes.first().value)
    }


    @Test
    fun testOrganization() {
        val org = Organization().apply {
            values.add("Org")
            values.add("Dept")
        }
        val vCard = generate {
            organization = org
            jobTitle = "CEO"
            jobDescription = "Executive"
        }
        assertEquals(org, vCard.organization)
        assertEquals("CEO", vCard.titles.first().value)
        assertEquals("Executive", vCard.roles.first().value)
    }


    @Test
    fun testPhoto() {
        val testPhoto = ByteArray(128)
        val vCard = generate { photo = testPhoto }
        assertEquals(Photo(testPhoto, ImageType.JPEG), vCard.photos.first())
    }


    @Test
    fun testRelation_vCard3_Assistant() {   // combination of custom type (assistant) and vCard4 standard type (co-worker)
        val vCard = generate(version = VCardVersion.V3_0) {
            relations += Related().apply {
                text = "My Assistant"
                types.add(CustomType.Related.ASSISTANT)
                types.add(RelatedType.CO_WORKER)
            }
        }
        vCard.getProperty(XAbRelatedNames::class.java).apply {
            assertEquals("My Assistant", value)
            assertEquals("item1", group)
        }
        vCard.getProperty(XAbLabel::class.java).apply {
            assertEquals(XAbRelatedNames.APPLE_ASSISTANT, value)
            assertEquals("item1", group)
        }
        assertTrue(vCard.relations.isEmpty())
    }

    @Test
    fun testRelation_vCard3_Child() {       // vCard4 standard type
        val vCard = generate(version = VCardVersion.V3_0) {
            relations += Related().apply {
                text = "My Child"
                types.add(RelatedType.CHILD)
            }
        }
        vCard.getProperty(XAbRelatedNames::class.java).apply {
            assertEquals("My Child", value)
            assertEquals("item1", group)
        }
        vCard.getProperty(XAbLabel::class.java).apply {
            assertEquals(XAbRelatedNames.APPLE_CHILD, value)
            assertEquals("item1", group)
        }
        assertTrue(vCard.relations.isEmpty())
    }

    @Test
    fun testRelation_vCard3_Custom() {
        val vCard = generate(version = VCardVersion.V3_0) {
            relations += Related().apply {
                text = "Someone"
                types.add(RelatedType.get("Custom Relationship"))
            }
        }
        vCard.getProperty(XAbRelatedNames::class.java).apply {
            assertEquals("Someone", value)
            assertEquals("item1", group)
        }
        vCard.getProperty(XAbLabel::class.java).apply {
            assertEquals("Custom Relationship", value)
            assertEquals("item1", group)
        }
        assertTrue(vCard.relations.isEmpty())
    }

    @Test
    fun testRelation_vCard3_Other() {
        val rel = Related.email("bigbrother@example.com")
        val vCard = generate(version = VCardVersion.V3_0) { relations += rel }
        vCard.getProperty(XAbRelatedNames::class.java).apply {
            assertEquals("mailto:bigbrother@example.com", value)
            assertEquals("other", getParameter("TYPE"))
            assertNull(group)
        }
        assertTrue(vCard.relations.isEmpty())
    }

    @Test
    fun testRelation_vCard3_Partner() {       // custom type
        val vCard = generate(version = VCardVersion.V3_0) {
            relations += Related().apply {
                text = "My Partner"
                types.add(CustomType.Related.PARTNER)
            }
        }
        vCard.getProperty(XAbRelatedNames::class.java).apply {
            assertEquals("My Partner", value)
            assertEquals("item1", group)
        }
        vCard.getProperty(XAbLabel::class.java).apply {
            assertEquals(XAbRelatedNames.APPLE_PARTNER, value)
            assertEquals("item1", group)
        }
        assertTrue(vCard.relations.isEmpty())
    }

    @Test
    fun testRelation_vCard4() {
        val rel = Related.email("bigbrother@example.com")
        val vCard = generate(version = VCardVersion.V4_0) { relations += rel }
        assertEquals(rel, vCard.relations.first())
    }


    @Test
    fun testTel() {
        val vCard = generate {
            phoneNumbers.add(LabeledProperty(Telephone("+1 555 12345")))
        }
        assertEquals("+1 555 12345", vCard.telephoneNumbers.first().text)
    }


    @Test
    fun testUid() {
        val vCard = generate { uid = "12345" }
        assertEquals("12345", vCard.uid.value)
    }


    @Test
    fun testUnknownProperty() {
        val vCard = generate {
            unknownProperties = "BEGIN:VCARD\r\n" +
                    "FUTURE-PROPERTY;X-TEST=test1;TYPE=uri:12345\r\n" +
                    "END:VCARD\r\n"
        }
        assertEquals("12345", vCard.getExtendedProperty("FUTURE-PROPERTY").value)
        assertEquals("test1", vCard.getExtendedProperty("FUTURE-PROPERTY").getParameter("X-TEST"))
    }


    @Test
    fun testUrl() {
        val vCard = generate { urls += LabeledProperty(Url("https://example.com")) }
        assertEquals("https://example.com", vCard.urls.first().value)
    }


    @Test
    fun testXPhoneticName() {
        val vCard = generate {
            phoneticGivenName = "Given"
            phoneticMiddleName = "Middle"
            phoneticFamilyName = "Family"
        }
        assertEquals("Given", vCard.getProperty(XPhoneticFirstName::class.java).value)
        assertEquals("Middle", vCard.getProperty(XPhoneticMiddleName::class.java).value)
        assertEquals("Family", vCard.getProperty(XPhoneticLastName::class.java).value)
    }



    // test generator helpers

    @Test
    fun testAddLabeledProperty_NoLabel() {
        val vCard = generate {
            nickName = LabeledProperty(Nickname().apply {
                values.add("nick1")
            })
        }
        assertEquals(4 /* PRODID + NICK + REV + FN */, vCard.properties.size)
        assertEquals("nick1", vCard.nickname.values.first())
    }

    @Test
    fun testAddLabeledProperty_Label() {
        val vCard = generate {
            nickName = LabeledProperty(Nickname().apply {
                values.add("nick1")
            }, "label1")
        }
        assertEquals(5 /* PRODID + NICK + X-ABLABEL + FN + REV */, vCard.properties.size)
        vCard.nickname.apply {
            assertEquals("nick1", values.first())
            assertEquals("item1", group)
        }
        vCard.getProperty(XAbLabel::class.java).apply {
            assertEquals("label1", value)
            assertEquals("item1", group)
        }
    }

    @Test
    fun testAddLabeledProperty_Label_CollisionWithUnknownProperty() {
        val vCard = generate {
            unknownProperties = "BEGIN:VCARD\n" +
                    "item1.X-TEST:This property is blocking the first item ID\n" +
                    "END:VCARD"
            nickName = LabeledProperty(Nickname().apply {
                values.add("nick1")
            }, "label1")
        }
        assertEquals(6 /* PRODID + X-TEST + NICK + X-ABLABEL + FN + REV */, vCard.properties.size)
        vCard.nickname.apply {
            assertEquals("nick1", values.first())
            assertEquals("item2", group)
        }
        vCard.getProperty(XAbLabel::class.java).apply {
            assertEquals("label1", value)
            assertEquals("item2", group)
        }
    }


    @Test
    fun testRewritePartialDate_vCard3_Date() {
        val generator = ContactWriter(Contact(), VCardVersion.V3_0, testProductId)
        val date = Birthday(LocalDate.of(121, 6, 30))
        generator.rewritePartialDate(date)
        assertEquals(LocalDate.of(121, 6, 30), date.date)
        assertNull(date.partialDate)
    }

    @Test
    fun testRewritePartialDate_vCard4_Date() {
        val generator = ContactWriter(Contact(), VCardVersion.V4_0, testProductId)
        val date = Birthday(LocalDate.of(121, 6, 30))
        generator.rewritePartialDate(date)
        assertEquals(LocalDate.of(121, 6, 30), date.date)
        assertNull(date.partialDate)
        assertEquals(0, date.parameters.size())
    }

    @Test
    fun testRewritePartialDate_vCard3_PartialDateWithYear() {
        val generator = ContactWriter(Contact(), VCardVersion.V3_0, testProductId)
        val date = Birthday(PartialDate.parse("20210730"))
        generator.rewritePartialDate(date)
        assertEquals(LocalDate.of(2021, 7, 30), date.date)
        assertNull(date.partialDate)
        assertEquals(0, date.parameters.size())
    }

    @Test
    fun testRewritePartialDate_vCard4_PartialDateWithYear() {
        val generator = ContactWriter(Contact(), VCardVersion.V4_0, testProductId)
        val date = Birthday(PartialDate.parse("20210730"))
        generator.rewritePartialDate(date)
        assertNull(date.date)
        assertEquals(PartialDate.parse("20210730"), date.partialDate)
        assertEquals(0, date.parameters.size())
    }

    @Test
    fun testRewritePartialDate_vCard3_PartialDateWithoutYear() {
        val generator = ContactWriter(Contact(), VCardVersion.V3_0, testProductId)
        val date = Birthday(PartialDate.parse("--0730"))
        generator.rewritePartialDate(date)
        assertEquals(LocalDate.of(1604, 7, 30), date.date)
        assertNull(date.partialDate)
        assertEquals(1, date.parameters.size())
        assertEquals("1604", date.getParameter(Contact.DATE_PARAMETER_OMIT_YEAR))
    }

    @Test
    fun testRewritePartialDate_vCard4_PartialDateWithoutYear() {
        val generator = ContactWriter(Contact(), VCardVersion.V4_0, testProductId)
        val date = Birthday(PartialDate.parse("--0730"))
        generator.rewritePartialDate(date)
        assertNull(date.date)
        assertEquals(PartialDate.parse("--0730"), date.partialDate)
        assertEquals(0, date.parameters.size())
    }


    // helpers

    private fun generate(version: VCardVersion = VCardVersion.V4_0, prepare: Contact.() -> Unit): VCard {
        val contact = Contact()
        contact.run(prepare)
        return ContactWriter(contact, version, testProductId).vCard
    }

}