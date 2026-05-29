/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.UriPropertyScribe
import ezvcard.property.Member

class XAddressBookServerMember(value: String?): Member(value) {

    object Scribe :
        UriPropertyScribe<XAddressBookServerMember>(XAddressBookServerMember::class.java, "X-ADDRESSBOOKSERVER-MEMBER") {

        override fun _parseValue(value: String?) = XAddressBookServerMember(value)

    }

}