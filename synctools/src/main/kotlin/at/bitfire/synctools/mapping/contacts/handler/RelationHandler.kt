/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Relation
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.util.trimToNull
import at.bitfire.synctools.vcard.property.CustomType
import ezvcard.parameter.RelatedType
import ezvcard.property.Related

object RelationHandler: DataRowHandler() {

    override fun forMimeType() = Relation.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)
        val name = values.getAsString(Relation.NAME) ?: return

        val related = Related()
        related.text = name

        when (values.getAsInteger(Relation.TYPE)) {
            Relation.TYPE_ASSISTANT -> {
                related.types += CustomType.Related.ASSISTANT
                related.types += RelatedType.CO_WORKER
            }
            Relation.TYPE_BROTHER -> {
                related.types += CustomType.Related.BROTHER
                related.types += RelatedType.SIBLING
            }
            Relation.TYPE_CHILD ->
                related.types += RelatedType.CHILD
            Relation.TYPE_DOMESTIC_PARTNER -> {
                related.types += CustomType.Related.DOMESTIC_PARTNER
                related.types += RelatedType.SPOUSE
            }
            Relation.TYPE_FATHER -> {
                related.types += CustomType.Related.FATHER
                related.types += RelatedType.PARENT
            }
            Relation.TYPE_FRIEND ->
                related.types += RelatedType.FRIEND
            Relation.TYPE_MANAGER -> {
                related.types += CustomType.Related.MANAGER
                related.types += RelatedType.CO_WORKER
            }
            Relation.TYPE_MOTHER -> {
                related.types += CustomType.Related.MOTHER
                related.types += RelatedType.PARENT
            }
            Relation.TYPE_PARENT ->
                related.types += RelatedType.PARENT
            Relation.TYPE_PARTNER ->
                related.types += CustomType.Related.PARTNER
            Relation.TYPE_REFERRED_BY ->
                related.types += CustomType.Related.REFERRED_BY
            Relation.TYPE_RELATIVE ->
                related.types += RelatedType.KIN
            Relation.TYPE_SISTER -> {
                related.types += CustomType.Related.SISTER
                related.types += RelatedType.SIBLING
            }
            Relation.TYPE_SPOUSE ->
                related.types += RelatedType.SPOUSE
            Relation.TYPE_CUSTOM ->
                values.getAsString(Relation.LABEL).trimToNull()?.let { typeStr ->
                    for (type in typeStr.split(','))
                        related.types += RelatedType.get(type.lowercase().trim())
                }
        }

        contact.relations += related
    }

}