/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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