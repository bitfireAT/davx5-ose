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
import java.time.DateTimeException
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneRules
import java.util.logging.Logger

/**
 * A [TimeZoneRegistry] that uses system timezone IDs for all TZIDs recognised by [ZoneId.of],
 * ignoring any VTIMEZONE component provided alongside such a TZID.
 *
 * Workarounds:
 * - https://github.com/bitfireAT/davx5-ose/issues/2248 – NPE when receiving calendar event with invalid VTIMEZONE
 * - https://github.com/bitfireAT/davx5-ose/issues/2525 – Don't use custom timezone when TZID is available in system
 * - https://github.com/bitfireAT/davx5/issues/914 – Rare NPE in ical4j when ZoneIdPool runs out of available ZoneIds
 */
class SystemAwareTimeZoneRegistry(
    /** Underlying registry that handles storage and custom-VTIMEZONE resolution. */
    private val timeZoneRegistry: TimeZoneRegistry = DefaultTimeZoneRegistryFactory().createRegistry()
) : TimeZoneRegistry by timeZoneRegistry {

    private val logger: Logger
        get() = Logger.getLogger(javaClass.name)

    /** Delegates to our [register] (not the one of [timeZoneRegistry]). */
    override fun register(timeZone: TimeZone) {
        register(timeZone, update = false)
    }

    /**
     * Registers [timeZone] in the underlying registry, with the following exceptions:
     *
     * - If the TZID is known to the system (like `Europe/Berlin`), registration is skipped
     *   so that [getZoneId] returns the system [ZoneId] directly without allocating an
     *   `ical4j-local-*` pool slot.
     * - If the VTIMEZONE has no STANDARD or DAYLIGHT observances, it is invalid and ignored.
     */
    override fun register(timeZone: TimeZone, update: Boolean) {
        // Note: ZoneId.of() is case-sensitive, so we can query available zone IDs directly
        if (ZoneId.getAvailableZoneIds().contains(timeZone.id)) {
            logger.fine("Skipping VTIMEZONE registration for system-known TZID: ${timeZone.id}")
            return
        }

        // Don't register empty (and thus invalid) VTIMEZONEs
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

    /**
     * Required hack: returns a non-empty zone-rules map so that
     * [net.fortuna.ical4j.model.parameter.TzId.toZoneId] always calls [getZoneId]
     * (overridden below) rather than falling through to
     * [TimeZoneRegistry.getGlobalZoneId], which would return `ical4j~UUID` zones.
     */
    override fun getZoneRules(): Map<String, ZoneRules> =
        timeZoneRegistry.zoneRules.takeIf { it.isNotEmpty() } ?: NON_EMPTY_ZONE_RULES

    /**
     * Returns the system [ZoneId] directly for TZIDs recognised by [ZoneId.of], instead
     * of `ical4j-local-*` or `ical4j~UUID` zones. Falls back to the underlying
     * registry for custom TZIDs.
     */
    override fun getZoneId(tzId: String): ZoneId =
        try {
            ZoneId.of(tzId)
        } catch (_: DateTimeException) {
            timeZoneRegistry.getZoneId(tzId)
        }

    private companion object {
        val NON_EMPTY_ZONE_RULES: Map<String, ZoneRules> =
            mapOf("_ical4j_zone_" to ZoneOffset.UTC.rules)
    }

    /* referenced in ical4j.properties */
    class Factory : TimeZoneRegistryFactory() {
        override fun createRegistry() = SystemAwareTimeZoneRegistry()
    }

}
