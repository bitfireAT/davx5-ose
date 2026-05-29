/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XPhoneticFirstName(value: String?): TextProperty(value) {

    object Scribe :
        StringPropertyScribe<XPhoneticFirstName>(XPhoneticFirstName::class.java, "X-PHONETIC-FIRST-NAME") {

        override fun _parseValue(value: String?) = XPhoneticFirstName(value)

    }

}