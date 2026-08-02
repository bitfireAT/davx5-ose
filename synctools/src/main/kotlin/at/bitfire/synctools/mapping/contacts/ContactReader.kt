/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts

import at.bitfire.synctools.util.trimToNull
import at.bitfire.synctools.vcard.property.CustomScribes.registerCustomScribes
import at.bitfire.synctools.vcard.property.CustomType
import at.bitfire.synctools.vcard.property.XAbDate
import at.bitfire.synctools.vcard.property.XAbLabel
import at.bitfire.synctools.vcard.property.XAbRelatedNames
import at.bitfire.synctools.vcard.property.XPhoneticFirstName
import at.bitfire.synctools.vcard.property.XPhoneticLastName
import at.bitfire.synctools.vcard.property.XPhoneticMiddleName
import at.bitfire.synctools.vcard.property.XSip
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.parameter.RelatedType
import ezvcard.property.Address
import ezvcard.property.Anniversary
import ezvcard.property.Birthday
import ezvcard.property.Categories
import ezvcard.property.DateOrTimeProperty
import ezvcard.property.Email
import ezvcard.property.FormattedName
import ezvcard.property.Impp
import ezvcard.property.Kind
import ezvcard.property.Label
import ezvcard.property.Logo
import ezvcard.property.Member
import ezvcard.property.Nickname
import ezvcard.property.Note
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.ProductId
import ezvcard.property.Related
import ezvcard.property.Revision
import ezvcard.property.Role
import ezvcard.property.SortString
import ezvcard.property.Sound
import ezvcard.property.Source
import ezvcard.property.StructuredName
import ezvcard.property.Telephone
import ezvcard.property.Title
import ezvcard.property.Uid
import ezvcard.property.Url
import ezvcard.util.PartialDate
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.time.temporal.Temporal
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Responsible for converting a specific vCard with a specific version to
 * the version-independent data class [Contact].
 *
 * Attention: This class works with the original vCard and modifies it!
 */
class ContactReader internal constructor(val vCard: VCard, val downloader: Contact.Downloader? = null) {

    companion object {
        
        private val logger
            get() = Logger.getLogger(ContactReader::class.java.name)

        /**
         * Maximum size for binary data like LOGO and SOUND data URIs.
         *
         * Data larger than this size has unfortunately to be dropped because of Android's IPC limits
         * (max 1 MB per transaction, for all rows together).
         */
        const val MAX_BINARY_DATA_SIZE = 25*1024

        suspend fun fromVCard(vCard: VCard, downloader: Contact.Downloader? = null) =
            ContactReader(vCard, downloader).toContact()

        /**
         * In contrast to vCard 4, there are no dates without year (like birthdays) in vCard 3.
         * As a workaround, many servers use this format:
         *
         * `BDAY;X-APPLE-OMIT-YEAR=1604:1604-08-20`
         *
         * To use it properly, we have to convert dates of this format to [PartialDate] (in
         * our example, a [PartialDate] that represents `--0820`).
         *
         * This is what this method is for: it converts dates (y/m/d) with an `X-APPLE-OMIT-YEAR`
         * parameter to a [PartialDate] (-/m/d) when the year equals to the `X-APPLE-OMIT-YEAR`
         * parameter.
         *
         * [prop]'s [DateOrTimeProperty.date] must be a [LocalDate], [LocalDateTime] or [OffsetDateTime]
         * for this to work. [Instant] values will be ignored.
         *
         * @param prop date/time to check for partial dates; value may be replaced by [PartialDate]
         */
        fun checkPartialDate(prop: DateOrTimeProperty) {
            val date: Temporal? = prop.date

            if (arrayOf(ChronoField.YEAR, ChronoField.MONTH_OF_YEAR, ChronoField.DAY_OF_MONTH).any { date?.isSupported(it) == false }) {
                logger.warning("checkPartialDate: unsupported DateOrTimeProperty $prop")
                return
            }

            if (prop.partialDate == null && date != null) {
                prop.getParameter(Contact.DATE_PARAMETER_OMIT_YEAR)?.let { omitYearStr ->
                    if (date.get(ChronoField.YEAR).toString() == omitYearStr) {
                        val partial = PartialDate.builder()
                            .date(date.get(ChronoField.DAY_OF_MONTH))
                            .month(date.get(ChronoField.MONTH_OF_YEAR))
                            .build()
                        prop.partialDate = partial
                    }

                    // X-APPLE-OMIT-YEAR not required anymore because we're now working with PartialDate
                    prop.removeParameter(Contact.DATE_PARAMETER_OMIT_YEAR)
                }
            }
        }

        fun uriToUid(uriString: String?): String? {
            if (uriString == null)
                return null

            val uid = try {
                val uri = URI(uriString)
                when {
                    uri.scheme == null ->
                        uri.schemeSpecificPart
                    uri.scheme.equals("urn", true) && uri.schemeSpecificPart.startsWith("uuid:", true) ->
                        uri.schemeSpecificPart.substring(5)
                    else ->
                        uriString
                }
            } catch(e: URISyntaxException) {
                logger.warning("Invalid URI for UID: $uriString")
                uriString
            }

            return uid?.trimToNull()
        }

    }


    /**
     * Converts the vCard to a [Contact].
     */
    suspend fun toContact(): Contact {
        val c = Contact()

        // process standard properties; after processing, only unknown properties will remain
        for (prop in vCard.properties) {
            var remove = true       // assume that this property will be processed and thus shall be removed

            when (prop) {
                is Uid ->
                    c.uid = uriToUid(prop.value)

                is Kind ->      // includes XAddressBookServerKind
                    c.group = prop.isGroup
                is Member ->    // includes XAddressBookServerMember
                    uriToUid(prop.uri)?.let { c.members += it }

                is FormattedName ->
                    c.displayName = prop.value?.trimToNull()
                is StructuredName -> {
                    c.prefix = prop.prefixes.joinToString(" ").trimToNull()
                    c.givenName = prop.given?.trimToNull()
                    c.middleName = prop.additionalNames.joinToString(" ").trimToNull()
                    c.familyName = prop.family?.trimToNull()
                    c.suffix = prop.suffixes.joinToString(" ").trimToNull()
                }
                is XPhoneticFirstName ->
                    c.phoneticGivenName = prop.value?.trimToNull()
                is XPhoneticMiddleName ->
                    c.phoneticMiddleName = prop.value?.trimToNull()
                is XPhoneticLastName ->
                    c.phoneticFamilyName = prop.value?.trimToNull()
                is Nickname ->
                    c.nickName = LabeledProperty(prop, findAndRemoveLabel(prop.group))

                is Categories ->
                    c.categories.addAll(prop.values)

                is Organization ->
                    c.organization = prop
                is Title ->
                    c.jobTitle = prop.value?.trimToNull()
                is Role ->
                    c.jobDescription = prop.value?.trimToNull()

                is Telephone ->
                    c.phoneNumbers += LabeledProperty(prop, findAndRemoveLabel(prop.group))
                is Email ->
                    c.emails += LabeledProperty(prop, findAndRemoveLabel(prop.group))
                is Impp ->
                    c.impps += LabeledProperty(prop, findAndRemoveLabel(prop.group))
                is XSip ->
                    // special case: treat  X-SIP:address  as  IMPP:sip:address
                    c.impps += LabeledProperty(Impp.sip(prop.value))
                is Url ->
                    c.urls += LabeledProperty(prop, findAndRemoveLabel(prop.group))

                is Address ->
                    c.addresses += LabeledProperty(prop, findAndRemoveLabel(prop.group))
                is Label -> { /* drop vCard3 formatted address because it can't be associated to a specific address */ }

                is Anniversary -> {
                    checkPartialDate(prop)
                    c.anniversary = prop
                }
                is Birthday -> {
                    checkPartialDate(prop)
                    c.birthDay = prop
                }
                is XAbDate -> {
                    checkPartialDate(prop)
                    var label = findAndRemoveLabel(prop.group)
                    if (label == XAbLabel.APPLE_OTHER)              // drop Apple "Other" label
                        label = null

                    if (label == XAbLabel.APPLE_ANNIVERSARY) {      // convert custom date with Apple "Anniversary" label to real anniversary
                        if (prop.date != null)
                            c.anniversary = Anniversary(prop.date)
                        else if (prop.partialDate != null)
                            c.anniversary = Anniversary(prop.partialDate)
                    } else
                        c.customDates += LabeledProperty(prop, label)
                }

                is Related ->
                    if (!prop.uri.isNullOrBlank() || !prop.text.isNullOrBlank())
                        c.relations += prop
                is XAbRelatedNames -> {
                    val relation = Related()
                    relation.text = prop.value

                    when (val labelStr = findAndRemoveLabel(prop.group)) {
                        XAbRelatedNames.APPLE_ASSISTANT -> {
                            relation.types.add(CustomType.Related.ASSISTANT)
                            relation.types.add(RelatedType.CO_WORKER)
                        }
                        XAbRelatedNames.APPLE_BROTHER -> {
                            relation.types.add(CustomType.Related.BROTHER)
                            relation.types.add(RelatedType.SIBLING)
                        }
                        XAbRelatedNames.APPLE_CHILD ->
                            relation.types.add(RelatedType.CHILD)
                        XAbRelatedNames.APPLE_FATHER -> {
                            relation.types.add(CustomType.Related.FATHER)
                            relation.types.add(RelatedType.PARENT)
                        }
                        XAbRelatedNames.APPLE_FRIEND ->
                            relation.types.add(RelatedType.FRIEND)
                        XAbRelatedNames.APPLE_MANAGER -> {
                            relation.types.add(CustomType.Related.MANAGER)
                            relation.types.add(RelatedType.CO_WORKER)
                        }
                        XAbRelatedNames.APPLE_MOTHER -> {
                            relation.types.add(CustomType.Related.MOTHER)
                            relation.types.add(RelatedType.PARENT)
                        }
                        XAbRelatedNames.APPLE_SISTER -> {
                            relation.types.add(CustomType.Related.SISTER)
                            relation.types.add(RelatedType.SIBLING)
                        }
                        XAbRelatedNames.APPLE_PARENT ->
                            relation.types.add(RelatedType.PARENT)
                        XAbRelatedNames.APPLE_PARTNER ->
                            relation.types.add(CustomType.Related.PARTNER)
                        XAbRelatedNames.APPLE_SPOUSE ->
                            relation.types.add(RelatedType.SPOUSE)

                        is String /* label != null */ -> {
                            for (label in labelStr.split(','))
                                relation.types.add(RelatedType.get(label.trim().lowercase()))
                        }
                    }
                    c.relations += relation
                }

                is Note -> {
                    prop.value.trimToNull()?.let { note ->
                        if (c.note == null)
                            c.note = note
                        else
                            c.note += "\n\n\n" + note
                    }
                }

                is Photo ->
                    c.photo = c.photo ?: getPhotoBytes(prop)

                // drop large binary properties because of potential OutOfMemory / TransactionTooLarge exceptions
                is Logo -> {
                    remove = prop.data != null && prop.data.size > MAX_BINARY_DATA_SIZE
                }
                is Sound -> {
                    remove = prop.data != null && prop.data.size > MAX_BINARY_DATA_SIZE
                }

                // remove properties that don't apply anymore
                is ProductId,
                is Revision,
                is SortString,      // not counterpart in Android; remove it because FN/N may be changed, which would cause inconsistency
                is Source -> {      // when we upload a modified contact, the SOURCE would maybe point to a different version, which would cause inconsistency
                    /* remove = true */
                }

                else ->     // unknown property, keep it in vCard in order to retain it
                    remove = false
            }

            if (remove)
                vCard.removeProperty(prop)
        }

        if (c.uid == null) {
            logger.warning("Received vCard without UID, generating new one")
            c.uid = UUID.randomUUID().toString()
        }

        // remove properties which
        // - couldn't be parsed (and thus are treated as extended/unknown properties), and
        // - must not occur more than once
        arrayOf("ANNIVERSARY", "BDAY", "KIND", "FN", "N", "PRODID", "REV", "UID").forEach {
            vCard.removeExtendedProperty(it)
        }

        if (vCard.properties.isNotEmpty() || vCard.extendedProperties.isNotEmpty())
            try {
                c.unknownProperties = Ezvcard
                        .write(vCard)
                        .prodId(false)
                        .version(vCard.version)
                        .registerCustomScribes()
                        .go()
            } catch(e: Exception) {
                logger.log(Level.WARNING, "Couldn't serialize unknown properties, dropping them", e)
            }

        return c
    }


    // helpers

    fun findAndRemoveLabel(group: String?): String? {
        if (group == null)
            return null

        for (label in vCard.getProperties(XAbLabel::class.java)) {
            if (label.group.equals(group, true)) {
                vCard.removeProperty(label)
                return label.value.trimToNull()
            }
        }

        return null
    }

    suspend fun getPhotoBytes(photo: Photo): ByteArray? {
        if (photo.data != null)
            return photo.data

        val url = photo.url
        if (photo.url != null && downloader != null) {
            logger.info("Downloading photo from $url")
            return downloader.download(url, "image/*")
        }

        return null
    }

}