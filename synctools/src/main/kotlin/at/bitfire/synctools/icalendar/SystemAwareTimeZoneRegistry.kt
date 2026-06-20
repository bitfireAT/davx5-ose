/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.util.TimeApiExtensions
import net.fortuna.ical4j.model.DefaultTimeZoneRegistryFactory
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.ZoneRulesProviderImpl
import net.fortuna.ical4j.model.component.Observance
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneRules
import java.util.Collections
import java.util.logging.Logger

/**
 * A [TimeZoneRegistry] that uses system timezone IDs for all TZIDs recognised by [ZoneId.of],
 * ignoring any VTIMEZONE component provided alongside such a TZID.
 *
 * Workarounds:
 * - https://github.com/bitfireAT/davx5-ose/issues/2248
 * - https://github.com/bitfireAT/davx5-ose/issues/2525
 * - https://github.com/bitfireAT/davx5/issues/914
 */
class SystemAwareTimeZoneRegistry(
    private val timeZoneRegistry: TimeZoneRegistry = DefaultTimeZoneRegistryFactory().createRegistry()
) : TimeZoneRegistry by timeZoneRegistry {

    /**
     * Configured via `ical4j.properties`:
     * `net.fortuna.ical4j.timezone.registry=at.bitfire.synctools.icalendar.SystemAwareTimeZoneRegistry$Factory`
     */
    class Factory : TimeZoneRegistryFactory() {
        override fun createRegistry() = SystemAwareTimeZoneRegistry()
    }

    private companion object {
        // A non-empty map to ensure getZoneRules() is never empty. TzId.toZoneId() only calls
        // registry.getZoneId() (which we override below) when getZoneRules() is non-empty;
        // otherwise it falls through to getGlobalZoneId() which returns ical4j~UUID zones from
        // the classpath database (DefaultZoneRulesProvider pre-populates ZONE_IDS for all TZIDs).
        val NON_EMPTY_ZONE_RULES: Map<String, ZoneRules> =
            Collections.singletonMap("_ical4j_zone_", ZoneOffset.UTC.rules)
    }

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    override fun register(timeZone: TimeZone) {
        register(timeZone, update = false)
    }

    override fun register(timeZone: TimeZone, update: Boolean) {
        // Don't register VTIMEZONEs for system-known TZIDs: the system ZoneId should be
        // used directly via getZoneId(), so no ical4j-local-* pool slot should be allocated.
        if (TimeApiExtensions.isSystemTimezone(timeZone.id)) {
            logger.info("Skipping VTIMEZONE registration for system-known TZID: ${timeZone.id}")
            return
        }

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

    override fun getZoneRules(): Map<String, ZoneRules> =
        timeZoneRegistry.zoneRules.takeIf { it.isNotEmpty() } ?: NON_EMPTY_ZONE_RULES

    override fun getZoneId(tzId: String): ZoneId =
    // Return system ZoneId directly for known TZIDs; this prevents ical4j-local-* or ical4j~*
        // zones from being returned when a VTIMEZONE component is present alongside a system TZID.
        try {
            ZoneId.of(tzId)
        } catch (_: DateTimeException) {
            timeZoneRegistry.getZoneId(tzId)
        }
}
