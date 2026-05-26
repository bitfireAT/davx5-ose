/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard

import at.bitfire.synctools.vcard.property.CustomScribes.registerCustomScribes
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.io.text.VCardReader
import java.io.Reader
import javax.annotation.WillNotClose

class VCardParser {

    /**
     * Parses vCard data from a [Reader] into a list of [VCard] objects.
     *
     * Defaults to vCard version 3.0 and supports custom property scribes.
     *
     * @param reader The [Reader] providing the vCard data to parse. Will not be closed by this method.
     * @return List of parsed [VCard] objects.
     */
    fun parse(@WillNotClose reader: Reader): List<VCard> {
        // By default, CardDAV assumes vCard 3
        val vCards = VCardReader(reader, VCardVersion.V3_0)
            .registerCustomScribes()
            .readAll()

        return vCards
    }

}