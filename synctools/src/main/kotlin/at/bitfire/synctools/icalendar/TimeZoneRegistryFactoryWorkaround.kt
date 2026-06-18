/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.ZoneRulesProviderImpl
import net.fortuna.ical4j.model.component.Observance
import java.util.logging.Logger

class TimeZoneRegistryFactoryWorkaround : TimeZoneRegistryFactory() {
    override fun createRegistry(): TimeZoneRegistry {
        return TimeZoneRegistryWorkaround()
    }
}

/**
 * Work around
 * - https://github.com/bitfireAT/davx5-ose/issues/2248
 * - https://github.com/bitfireAT/davx5/issues/914
 */
class TimeZoneRegistryWorkaround(
    private val timeZoneRegistry: TimeZoneRegistry = DefaultTimeZoneRegistryFactory().createRegistry()
) : TimeZoneRegistry by timeZoneRegistry {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    override fun register(timeZone: TimeZone) {
        register(timeZone, update = false)
    }

    override fun register(timeZone: TimeZone, update: Boolean) {
        val vTimeZone = timeZone.vTimeZone

        val standardObservances = vTimeZone.getComponents<Observance>(Observance.STANDARD)
        val daylightObservances = vTimeZone.getComponents<Observance>(Observance.DAYLIGHT)

        if (standardObservances.isEmpty() && daylightObservances.isEmpty()) {
            logger.info("Ignoring invalid VTIMEZONE with TZID: ${timeZone.id}")
            return
        }

        if (ZoneRulesProviderImpl.INSTANCE.zoneIdPool.availableZoneIds() == 0) {
            // Trigger garbage collection in the hope that old TimeZoneRegistry instances will be freed and in turn
            // ZoneIdPool.cleanup() will be able to reclaim zone IDs that are no longer used.
            Runtime.getRuntime().gc()
        }

        timeZoneRegistry.register(timeZone, update)
    }
}
