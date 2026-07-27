/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import net.fortuna.ical4j.model.Property

/**
 * iCalendar `VALARM` property names that are mapped to dedicated `JtxContract.JtxAlarm` columns.
 *
 * Used to keep [at.bitfire.synctools.mapping.jtx.builder.RemindersBuilder] and
 * [at.bitfire.synctools.mapping.jtx.handler.RemindersHandler] in sync, which must not process these
 * properties when found in "other" properties.
 */
val KNOWN_ALARM_PROPERTIES: Set<String> = setOf(
    Property.ACTION,
    Property.ATTACH,
    Property.DESCRIPTION,
    Property.DURATION,
    Property.REPEAT,
    Property.SUMMARY,
    Property.TRIGGER
)
