/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.scribe.StringPropertyScribe
import ezvcard.property.TextProperty

class XPhoneticMiddleName(value: String?): TextProperty(value) {

    object Scribe :
        StringPropertyScribe<XPhoneticMiddleName>(XPhoneticMiddleName::class.java, "X-PHONETIC-MIDDLE-NAME") {

        override fun _parseValue(value: String?) = XPhoneticMiddleName(value)

    }

}