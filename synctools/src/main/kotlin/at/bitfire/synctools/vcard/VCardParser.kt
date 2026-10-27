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
     * Parses the first vCard from a [Reader].
     *
     * Defaults to vCard version 3.0 and supports custom property scribes.
     *
     * Malformed vCard content is currently parsed leniently (invalid lines or
     * values are skipped) rather than throwing an exception.
     *
     * @param reader The [Reader] providing the vCard data to parse. Will not be closed by this method.
     * @return the first parsed [VCard], or `null` if the reader didn't contain one.
     */
    fun parse(@WillNotClose reader: Reader): VCard? =
        // By default, CardDAV assumes vCard 3
        VCardReader(reader, VCardVersion.V3_0)
            .registerCustomScribes()
            .readNext()

}