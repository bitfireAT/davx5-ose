/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid

import at.bitfire.ical4android.ical4jVersion
import ezvcard.Ezvcard
import net.fortuna.ical4j.model.property.ProdId

/**
 * Brand-specific constants like (non-theme) colors, homepage URLs etc.
 */
object Constants {

    const val DAVDROID_GREEN_RGBA = 0xFF8bc34a.toInt()


    // product IDs for iCalendar/vCard

    val iCalProdId = ProdId("DAVx5/${BuildConfig.VERSION_NAME} ical4j/$ical4jVersion")
    const val vCardProdId = "+//IDN bitfire.at//DAVx5/${BuildConfig.VERSION_NAME} ez-vcard/${Ezvcard.VERSION}"

}