/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import at.bitfire.synctools.BuildConfig
import at.bitfire.synctools.exception.InvalidICalendarException
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.ParserException
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.ComponentContainer
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyContainer
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.TemporalAdapter
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.property.Sequence
import net.fortuna.ical4j.model.property.Uid
import java.io.StringReader
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

/**
 * The used version of ical4j.
 */
@Suppress("unused")
const val ical4jVersion = BuildConfig.version_ical4j


// DateProperty extensions

/**
 * Whether this date property represents an all-day date (VALUE=DATE).
 *
 * All-day dates use DATE values (not DATE-TIME). Returns `true` if this is a
 * [DateProperty] with DATE precision (no time component).
 *
 * @return `true` if this is a DATE value, `false` if it's a DATE-TIME value or null.
 */
fun DateProperty<*>?.isAllDay(): Boolean =
    this != null && !TemporalAdapter.isDateTimePrecision(date)


// component access helpers

fun<T: CalendarComponent> componentListOf(vararg components: T): ComponentList<T> =
    ComponentList(components.toList())

fun propertyListOf(vararg properties: Property): PropertyList =
    PropertyList(properties.toList())

val CalendarComponent.uid: Uid?
    get() = getProperty<Uid>(Property.UID).getOrNull()

val CalendarComponent.recurrenceId: RecurrenceId<*>?
    get() = getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()

val CalendarComponent.sequence: Sequence?
    get() = getProperty<Sequence>(Property.SEQUENCE).getOrNull()

fun <T: Temporal> CalendarComponent.dtStart(): DtStart<T>? {
    return getProperty<DtStart<T>>(Property.DTSTART).getOrNull()
}

fun <T: Temporal> CalendarComponent.dtEnd(): DtEnd<T>? {
    return getProperty<DtEnd<T>>(Property.DTEND).getOrNull()
}

fun <T: Temporal> CalendarComponent.due(): Due<T>? {
    return getProperty<Due<T>>(Property.DUE).getOrNull()
}

fun <T: Temporal> VEvent.requireDtStart(): DtStart<T> =
    getProperty<DtStart<T>>(Property.DTSTART).getOrNull() ?: throw InvalidICalendarException("Missing DTSTART in VEVENT")

/**
 * Whether this VTODO represents an all-day task.
 *
 * All-day tasks use DATE values (not DATE-TIME). Returns `true` if:
 * - DTSTART is a DATE value, or
 * - DTSTART is absent and DUE is a DATE value, or
 * - both DTSTART and DUE are absent.
 */
fun VToDo.isAllDay(): Boolean =
    dtStart<Temporal>()?.isAllDay()
        ?: due<Temporal>()?.isAllDay()
        ?: true

operator fun PropertyContainer.plusAssign(property: Property) {
    add<PropertyContainer>(property)
}

operator fun PropertyList.plusAssign(property: Property) {
    add(property)
}

operator fun Property.plusAssign(parameter: Parameter) {
    add<Property>(parameter)
}

operator fun <T : Component> ComponentContainer<T>.plusAssign(component: T) {
    add<ComponentContainer<T>>(component)
}


fun ProdId.withUserAgents(userAgents: List<String>): ProdId =
    if (userAgents.isEmpty())
        this
    else
        ProdId(value + " (${userAgents.joinToString(", ")})")

fun timezoneDefToTzId(timezoneDef: String): String? {
    try {
        val builder = CalendarBuilder()
        val cal = builder.build(StringReader(timezoneDef))
        val timezone = cal.getComponent<VTimeZone>(VTimeZone.VTIMEZONE).getOrNull()
        timezone?.timeZoneId?.let { return it.value }
    } catch (e: ParserException) {
        Logger.getLogger("at.bitfire.synctools.icalendar").log(Level.SEVERE, "Can't understand time zone definition", e)
    }
    return null
}