/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.vcard.property

import ezvcard.io.chain.ChainingTextWriter
import ezvcard.io.scribe.ScribeIndex
import ezvcard.io.text.VCardReader
import ezvcard.io.text.VCardWriter

object CustomScribes {

    /** list of all custom scribes (will be registered to readers/writers) **/
    private val customScribes = arrayOf(
        XAbDate.Scribe,
        XAbLabel.Scribe,
        XAbRelatedNames.Scribe,
        XAddressBookServerKind.Scribe,
        XAddressBookServerMember.Scribe,
        XPhoneticFirstName.Scribe,
        XPhoneticMiddleName.Scribe,
        XPhoneticLastName.Scribe,
        XSip.Scribe
    )


    fun ChainingTextWriter.registerCustomScribes(): ChainingTextWriter {
        for (scribe in customScribes)
            register(scribe)
        return this
    }

    fun ScribeIndex.registerCustomScribes() {
        for (scribe in customScribes)
            register(scribe)
    }

    fun VCardReader.registerCustomScribes(): VCardReader {
        scribeIndex.registerCustomScribes()
        return this
    }

    fun VCardWriter.registerCustomScribes() =
        scribeIndex.registerCustomScribes()

}
