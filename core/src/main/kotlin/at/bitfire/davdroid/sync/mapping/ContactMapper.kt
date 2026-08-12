/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import at.bitfire.dav4jvm.ktor.DavAddressBook
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalGroup
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.ContactWriter
import ezvcard.VCardVersion
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import java.io.StringWriter
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Maps [LocalAddress]es (contacts and groups) to vCard for upload.
 */
class ContactMapper @Inject constructor(
    private val logger: Logger,
    private val productIds: ProductIds
) : ResourceMapper<LocalAddress> {

    override fun generateUpload(
        resource: LocalAddress,
        capabilities: WebDavCollection.Capabilities
    ): GeneratedResource {
        val contact: Contact = when (resource) {
            is LocalContact -> resource.androidContact.getContact()
            is LocalGroup -> resource.androidGroup.getContact()
            else -> throw IllegalArgumentException("resource must be LocalContact or LocalGroup")
        }
        logger.log(Level.FINE, "Preparing upload of vCard #{0}: {1}", arrayOf(resource.id, contact))

        // get/create UID
        val (uid, uidIsGenerated) = DavUtils.generateUidIfNecessary(contact.uid)
        if (uidIsGenerated) {
            // modify in Contact and persist to contacts provider
            contact.uid = uid
            resource.updateUid(uid)
        }

        // generate vCard and convert to request body
        val writer = StringWriter()
        val mimeType: ContentType
        val vCardVersion: VCardVersion
        when {
            capabilities.supportsVCard4 -> {
                mimeType = DavAddressBook.MIME_VCARD4
                vCardVersion = VCardVersion.V4_0
            }
            else -> {
                mimeType = DavAddressBook.MIME_VCARD3_UTF8
                vCardVersion = VCardVersion.V3_0
            }
        }
        ContactWriter(contact, vCardVersion, productIds.vCardProdId).writeVCard(writer)

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(uid, "vcf"),
            content = TextContent(text = writer.toString(), contentType = mimeType)
        )
    }

}
