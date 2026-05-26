/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.contacts.handler

import android.content.ContentValues
import android.provider.ContactsContract.CommonDataKinds.Organization
import at.bitfire.synctools.mapping.contacts.Contact

object OrganizationHandler: DataRowHandler() {

    override fun forMimeType() = Organization.CONTENT_ITEM_TYPE

    override fun handle(values: ContentValues, contact: Contact) {
        super.handle(values, contact)

        val company = values.getAsString(Organization.COMPANY)
        val department = values.getAsString(Organization.DEPARTMENT)
        if (company != null || department != null) {
            val org = ezvcard.property.Organization()
            company?.let { org.values += it }
            department?.let { org.values += it }
            contact.organization = org
        }

        values.getAsString(Organization.TITLE)?.let { contact.jobTitle = it }
        values.getAsString(Organization.JOB_DESCRIPTION)?.let { contact.jobDescription = it }
    }

}