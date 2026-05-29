/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.Test
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.logging.Logger

class AndroidTimeZonesTest {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    @Test
    fun testAndroidTimeZonesAvailableInIcal4j() {
        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        for (id in ZoneId.getAvailableZoneIds()) {
            val name = ZoneId.of(id).getDisplayName(TextStyle.FULL, Locale.US)
            val info = tzRegistry.getTimeZone(id)
            if (info == null)
                logger.warning("Android timezone $id ($name) is not available in the ical4j " +
                        "timezone database. When a local event is created with $id, it won't have a " +
                        "VTIMEZONE when uploaded.")
        }
    }

}