/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

import at.bitfire.synctools.util.Utils.capitalize
import at.bitfire.synctools.util.Utils.isEmpty
import at.bitfire.synctools.util.trimToNull
import at.bitfire.synctools.vcard.VCardGenerator
import at.bitfire.synctools.vcard.property.CustomType
import at.bitfire.synctools.vcard.property.XAbDate
import at.bitfire.synctools.vcard.property.XAbLabel
import at.bitfire.synctools.vcard.property.XAbRelatedNames
import at.bitfire.synctools.vcard.property.XAddressBookServerKind
import at.bitfire.synctools.vcard.property.XAddressBookServerMember
import at.bitfire.synctools.vcard.property.XPhoneticFirstName
import at.bitfire.synctools.vcard.property.XPhoneticLastName
import at.bitfire.synctools.vcard.property.XPhoneticMiddleName
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.parameter.RelatedType
import ezvcard.property.Categories
import ezvcard.property.DateOrTimeProperty
import ezvcard.property.Kind
import ezvcard.property.Member
import ezvcard.property.Photo
import ezvcard.property.ProductId
import ezvcard.property.Related
import ezvcard.property.Revision
import ezvcard.property.StructuredName
import ezvcard.property.Uid
import ezvcard.property.VCardProperty
import java.io.Writer
import java.time.LocalDate
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Responsible for converting the [Contact] data class (which is not version-specific)
 * to the vCard that is actually sent to the server.
 *
 * Properties which are not supported by the target vCard version have to be converted appropriately.
 *
 * @param contact       contact data to be converted into a vCard
 * @param version       vCard version to generate
 * @param productId     product ID that identifies your app (will be used as PRODID; ez-vcard version will be appended)
 */
class ContactWriter(
    val contact: Contact,
    val version: VCardVersion,
    val productId: String
) {

    private val unknownProperties = LinkedList<VCardProperty>()
    val vCard = VCard()

    /** counter for item ID of labelled properties: 1 means "item1." etc. */
    private var currentItemId = 1

    private val logger
        get() = Logger.getLogger(javaClass.name)


    init {
        parseUnknownProperties()
        addProperties()
    }

    private fun addProperties() {
        vCard.productId = ProductId(productId)
        contact.uid?.let { vCard.uid = Uid(it) }

        addKindAndMembers()
        addFormattedName()
        addStructuredName()
        addPhoneticName()
        contact.nickName?.let { nickName -> addLabeledProperty(nickName) }

        if (contact.categories.isNotEmpty())
            vCard.addCategories(Categories().apply {
                values.addAll(contact.categories)
            })

        addOrganization()

        for (phone in contact.phoneNumbers)
            addLabeledProperty(phone)
        for (email in contact.emails)
            addLabeledProperty(email)
        for (impp in contact.impps)
            addLabeledProperty(impp)
        for (url in contact.urls)
            addLabeledProperty(url)

        for (address in contact.addresses)
            addLabeledProperty(address)

        addDates()

        for (relation in contact.relations)
            addRelation(relation)

        contact.note?.let { note -> vCard.addNote(note) }

        for (unknownProperty in unknownProperties)
            vCard.addProperty(unknownProperty)

        contact.photo?.let { photo -> vCard.addPhoto(Photo(photo, ImageType.JPEG)) }

        vCard.revision = Revision.now()
    }

    private fun addDates() {
        contact.birthDay?.let { birthday ->
            // vCard3 doesn't support partial dates
            if (version == VCardVersion.V3_0)
                rewritePartialDate(birthday)

            vCard.birthday = birthday
        }

        contact.anniversary?.let { anniversary ->
            if (version == VCardVersion.V4_0) {
                vCard.anniversary = anniversary

            } else /* version == VCardVersion.V3_0 */ {
                // vCard3 doesn't support partial dates
                rewritePartialDate(anniversary)
                // vCard3 doesn't support ANNIVERSARY, rewrite to X-ABDATE
                addLabeledProperty(LabeledProperty(XAbDate(anniversary.date), XAbLabel.APPLE_ANNIVERSARY))
                vCard.anniversary = null
            }
        }

        for (customDate in contact.customDates) {
            rewritePartialDate(customDate.property)
            addLabeledProperty(customDate)
        }
    }

    private fun addFormattedName() {
        // vCard 3 REQUIRES FN [RFC 2426 p. 29]
        // vCard 4 REQUIRES FN [RFC 6350 6.2.1 FN]
        val fn =
            // use display name, if available
            contact.displayName.trimToNull() ?:
            // no display name, try organization
            contact.organization?.values?.joinToString(" / ") ?:
            // otherwise, try nickname
            contact.nickName?.property?.values?.firstOrNull() ?:
            // otherwise, try email address
            contact.emails.firstOrNull()?.property?.value ?:
            // otherwise, try phone number
            contact.phoneNumbers.firstOrNull()?.property?.text ?:
            // otherwise, try UID or use empty string
            contact.uid ?: ""
        vCard.setFormattedName(fn)
    }

    private fun addKindAndMembers() {
        if (contact.group) {
            if (version == VCardVersion.V4_0) {         // vCard4
                vCard.kind = Kind.group()
                for (member in contact.members)
                    vCard.addMember(Member("urn:uuid:$member"))
            } else {                                    // "vCard4 as vCard3" (Apple-style)
                vCard.setProperty(XAddressBookServerKind(Kind.GROUP))
                for (member in contact.members)
                    vCard.addProperty(XAddressBookServerMember("urn:uuid:$member"))
            }
        }
    }

    private fun addOrganization() {
        contact.organization?.let { vCard.organization = it }
        contact.jobTitle?.let { vCard.addTitle(it) }
        contact.jobDescription?.let { vCard.addRole(it) }
    }

    private fun addRelation(relation: Related) {
        if (version == VCardVersion.V4_0)
            vCard.addRelated(relation)

        else /* version == VCardVersion.V3_0 */ {
            val name = XAbRelatedNames(relation.text ?: relation.uri)
            var label: String? = null

            val types = LinkedList(relation.types)
            types.remove(CustomType.Related.OTHER)       // ignore this type (has to be inserted by ContactReader when no type is set)

            when {
                types.contains(CustomType.Related.ASSISTANT) ->
                    label = XAbRelatedNames.APPLE_ASSISTANT
                types.contains(CustomType.Related.BROTHER) ->
                    label = XAbRelatedNames.APPLE_BROTHER
                types.contains(RelatedType.CHILD) ->
                    label = XAbRelatedNames.APPLE_CHILD
                types.contains(CustomType.Related.FATHER) ->
                    label = XAbRelatedNames.APPLE_FATHER
                types.contains(RelatedType.FRIEND) ->
                    label = XAbRelatedNames.APPLE_FRIEND
                types.contains(CustomType.Related.MANAGER) ->
                    label = XAbRelatedNames.APPLE_MANAGER
                types.contains(CustomType.Related.MOTHER) ->
                    label = XAbRelatedNames.APPLE_MOTHER
                types.contains(RelatedType.PARENT) ->
                    label = XAbRelatedNames.APPLE_PARENT
                types.contains(CustomType.Related.PARTNER) ->
                    label = XAbRelatedNames.APPLE_PARTNER
                types.contains(CustomType.Related.SISTER) ->
                    label = XAbRelatedNames.APPLE_SISTER
                types.contains(RelatedType.SPOUSE) ->
                    label = XAbRelatedNames.APPLE_SPOUSE

                else -> {
                    if (relation.types.isEmpty())
                        name.addParameter("TYPE", "other")
                    else
                        label = relation.types.joinToString(", ") { type ->
                            type.value.capitalize()
                        }
                }
            }

            addLabeledProperty(LabeledProperty(name, label))
        }
    }

    private fun addStructuredName() {
        val n = StructuredName()

        contact.prefix?.let { prefixesStr ->
            for (prefix in prefixesStr.split(' '))
                n.prefixes += prefix
        }

        n.given = contact.givenName
        contact.middleName?.let { middleNamesStr ->
            for (middleName in middleNamesStr.split(' '))
                n.additionalNames += middleName
        }
        n.family = contact.familyName

        contact.suffix?.let { suffixesStr ->
            for (suffix in suffixesStr.split(' '))
                n.suffixes += suffix
        }

        if (version == VCardVersion.V4_0) {
            // add N only if there's some data in it
            if (!n.isEmpty())
                vCard.structuredName = n

        } else if (version == VCardVersion.V3_0) {
            // vCard 3 REQUIRES N [RFC 2426 p. 29]

            // don't use empty N for vCard3 groups (otherwise they appear with name ";;;;;" in Apple)
            if (contact.group && n.isEmpty())
                n.family = contact.displayName

            vCard.structuredName = n
        }
    }

    private fun addPhoneticName() {
        contact.phoneticGivenName?.let { firstName ->
            vCard.addProperty(XPhoneticFirstName(firstName))
        }
        contact.phoneticMiddleName?.let { middleName ->
            vCard.addProperty(XPhoneticMiddleName(middleName))
        }
        contact.phoneticFamilyName?.let { lastName ->
            vCard.addProperty(XPhoneticLastName(lastName))
        }
    }


    // helpers

    fun addLabeledProperty(labeledProperty: LabeledProperty<*>) {
        val property = labeledProperty.property

        if (labeledProperty.label != null) {
            // property with label -> group property and label with item ID
            val itemId = getNextItemId()

            // 1. add property with item ID
            property.group = itemId

            // 2. add label with same item ID
            val label = XAbLabel(labeledProperty.label)
            label.group = itemId
            vCard.addProperty(label)
        }

        vCard.addProperty(property)
    }

    private fun getNextItemId(): String {
        var id: String

        // increase ID until there is one which is not already used by an unknown property
        do {
            id = "item${currentItemId++}"
        } while (unknownProperties.any { it.group == id })

        return id
    }

    private fun parseUnknownProperties() {
        try {
            contact.unknownProperties?.let {
                Ezvcard.parse(it).first()?.let { vCard ->
                    unknownProperties.addAll(vCard.properties)
                }
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't parse original vCard with retained properties", e)
        }
    }

    fun<T: DateOrTimeProperty> rewritePartialDate(prop: T) {
        // vCard 3 doesn't understand partial dates:
        //   1. the syntax is different (vCard 3: 2021-03-07, partial date: 20210307)
        //   2. vCard 3 doesn't understand dates without year
        val partial = prop.partialDate
        if (version == VCardVersion.V3_0 && prop.date == null && partial != null) {
            val originalYear = partial.year
            val year = originalYear ?: Contact.DATE_PARAMETER_OMIT_YEAR_DEFAULT

            // use full date format
            prop.date = LocalDate.of(year, partial.month, partial.date)

            if (originalYear == null)
                prop.addParameter(Contact.DATE_PARAMETER_OMIT_YEAR, Contact.DATE_PARAMETER_OMIT_YEAR_DEFAULT.toString())
        }
    }


    /**
     * Validates and writes the contact to a writer as vCard.
     *
     * @param writer    where vCard is written to
     */
    fun writeVCard(writer: Writer) {
        validate()

        /* Normally include trailing semicolons for maximum compatibility, but don't include them
        for groups because Apple then shows "N:Group;;;;" as "Group;;;;". */
        val generator = VCardGenerator(
            targetVersion = version,
            includeTrailingSemicolons = !contact.group
        )
        generator.write(vCard, writer)
    }

    private fun validate() {
        // validate vCard and log results
        val validation = vCard.validate(version)
        if (!validation.isEmpty) {
            val msgs = LinkedList<String>()
            for ((key, warnings) in validation)
                msgs += "  * " + key?.javaClass?.simpleName + " - " + warnings?.joinToString(" | ")
            logger.log(Level.WARNING, "vCard validation warnings: {0}", arrayOf(msgs.joinToString(",")))
        }
    }

}