/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.vcard

import at.bitfire.synctools.vcard.property.CustomScribes.registerCustomScribes
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.io.text.VCardWriter
import java.io.Writer
import javax.annotation.WillNotClose

class VCardGenerator(
    private val targetVersion: VCardVersion,
    private val includeTrailingSemicolons: Boolean
) {

    /**
     * Writes a [VCard] to the specified [Writer] with custom configuration.
     *
     * _Note:_ This method doesn't flush the Writer.
     *
     * @param vCard The [VCard] to be written.
     * @param to The target [Writer] where the vCard data will be written.
     */
    fun write(vCard: VCard, @WillNotClose to: Writer) {
        val writer = VCardWriter(to, targetVersion).apply {
            isAddProdId = false     // We handle PRODID ourselves
            registerCustomScribes() // Handle our custom properties

            /* We usually want to include trailing semicolons for maximum compatibility. */
            isIncludeTrailingSemicolons = includeTrailingSemicolons

            // Use caret encoding for parameter values (RFC 6868)
            isCaretEncodingEnabled = true

            // Allow properties that are not defined in this vCard version
            isVersionStrict = false
        }

        // Write actual vCard
        writer.write(vCard)
    }

}